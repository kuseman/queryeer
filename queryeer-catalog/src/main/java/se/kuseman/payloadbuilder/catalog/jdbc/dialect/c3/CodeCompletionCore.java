// CSOFF
/*
 * Copyright © 2017 VMware, Inc. All Rights Reserved.
 *
 * SPDX-License-Identifier: MIT
 *
 * See LICENSE file for more info.
 *
 * Forked from com.vmware.antlr4-c3:antlr4-c3:1.1 into se.kuseman.payloadbuilder.catalog.jdbc.dialect.c3
 *
 * Changes vs upstream:
 *  - followSetsByATN changed from HashMap to ConcurrentHashMap to fix concurrent-access data corruption
 *    when multiple query tabs trigger completion simultaneously.
 *  - All showResult / showDebugOutput / debugOutputWithTransitions / showRuleStack debug flags now default
 *    to false to eliminate per-state StringBuilder allocations and logger-level checks in the hot path.
 *  - collectFollowSets: replaced LinkedList.indexOf() O(n) cycle detection with a companion HashSet<Integer>
 *    for O(1) membership tests during ATN rule-recursion cycle detection.
 *  - getFollowingTokens / processRule: replaced LinkedList pipeline with ArrayDeque for O(1) add/remove.
 *  - translateToRuleIndex: fixed logger.fine("=====> collected: " + this.ruleNames[i]) where i was the
 *    loop index into ruleStack rather than the rule id — now logs ruleNames[ruleStack.get(i)] correctly.
 *  - collectFollowSets: switched from global 'seen' HashSet to path-scoped stateStack (add/remove) so that
 *    the same ATN state can be reached via multiple paths; returns boolean isExhaustive.
 *  - FollowSetsHolder: added isExhaustive flag; processRule now skips a rule only when the follow set is
 *    exhaustive AND the current symbol is absent, rather than relying on a sentinel Token.EPSILON entry.
 *  - collectFollowSets / processRule: when a called rule is not exhaustive (can reach RULE_STOP), the
 *    rule's followState is also explored so completions after the rule are not missed.
 *  - processRule: added precedenceStack and Transition.PRECEDENCE handling for left-recursive rules
 *    (e.g., operator-precedence expressions in SQL grammars).
 *  - processRule: token-following sequences are merged via longestCommonPrefix instead of clearing to empty
 *    on conflict, producing better "press tab" continuation suggestions.
 */
package se.kuseman.payloadbuilder.catalog.jdbc.dialect.c3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.PrecedencePredicateTransition;
import org.antlr.v4.runtime.atn.PredicateTransition;
import org.antlr.v4.runtime.atn.RuleStartState;
import org.antlr.v4.runtime.atn.RuleStopState;
import org.antlr.v4.runtime.atn.RuleTransition;
import org.antlr.v4.runtime.atn.Transition;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * Forked from the antlr4-c3 port of the antlr-c3 javascript library.
 * <p>
 * The c3 engine provides code completion candidates for editors with ANTLR generated parsers, independent of the actual language/grammar used for the generation.
 * <p>
 * See class-level javadoc for a summary of changes vs upstream.
 */
public class CodeCompletionCore
{

    public static final Logger logger = Logger.getLogger(CodeCompletionCore.class.getName());

    /**
     * JDO returning information about matching tokens and rules
     */
    public static class CandidatesCollection
    {
        /**
         * Collection of Token ID candidates, each with a follow-on List of subsequent tokens
         */
        public Map<Integer, List<Integer>> tokens = new HashMap<>();
        /**
         * Collection of Rule candidates, each with the callstack of rules to reach the candidate
         */
        public Map<Integer, List<Integer>> rules = new HashMap<>();
        /**
         * Collection of matched Preferred Rules each with their start and end offsets
         */
        public Map<Integer, List<Integer>> rulePositions = new HashMap<>();

        @Override
        public String toString()
        {
            return "CandidatesCollection{" + "tokens=" + tokens + ", rules=" + rules + ", ruleStrings=" + rulePositions + '}';
        }
    }

    public static class FollowSetWithPath
    {
        public IntervalSet intervals;
        public List<Integer> path;
        public List<Integer> following;
    }

    /** Cached follow sets for an ATN state, combined set, and whether the set is exhaustive. */
    public static class FollowSetsHolder
    {
        public List<FollowSetWithPath> sets;
        public IntervalSet combined;
        /**
         * True when all paths through this rule were fully explored (no path terminated at RULE_STOP without being covered). When false, processRule must not skip the rule even if the current symbol
         * is absent from combined, because there may be valid continuations reachable after this rule returns.
         */
        public boolean isExhaustive;
    }

    /** Entry in the ATN simulation pipeline: an ATN state paired with a token-stream index. */
    public static class PipelineEntry
    {
        public PipelineEntry(ATNState state, Integer tokenIndex)
        {
            this.state = state;
            this.tokenIndex = tokenIndex;
        }

        ATNState state;
        Integer tokenIndex;
    }

    // Debug flags — all default to false to avoid per-state allocations in the hot path.
    // Enable only when actively debugging completion behaviour.
    private boolean showResult = false;
    private boolean showDebugOutput = false;
    private boolean debugOutputWithTransitions = false;
    private boolean showRuleStack = false;

    private Set<Integer> ignoredTokens = new HashSet<>();
    private Set<Integer> preferredRules = new HashSet<>();

    private Parser parser;
    private ATN atn;
    private Vocabulary vocabulary;
    private String[] ruleNames;
    private List<Token> tokens;

    private int tokenStartIndex = 0;
    private int statesProcessed = 0;

    // A mapping of rule index to token stream position to end token positions.
    // A rule visited before with the same input position always produces the same output positions.
    private final Map<Integer, Map<Integer, Set<Integer>>> shortcutMap = new HashMap<>();
    private final CandidatesCollection candidates = new CandidatesCollection();

    /**
     * Precedence stack for left-recursive rules. Each entry is the minimum precedence required to enter the rule at that nesting level. Pushed when entering a left-recursive rule, popped on exit.
     */
    private List<Integer> precedenceStack = new ArrayList<>();

    /**
     * Per-grammar follow-set cache, keyed by parser class name then ATN state number.
     *
     * <p>
     * FIX: was a plain static HashMap — concurrent writes from multiple tabs caused ConcurrentModificationException / silent data corruption. Changed to ConcurrentHashMap. The inner maps are written
     * only once per (parser class, state) pair and thereafter only read, so ConcurrentHashMap on the outer level is sufficient.
     */
    private static final Map<String, Map<Integer, FollowSetsHolder>> followSetsByATN = new ConcurrentHashMap<>();

    public CodeCompletionCore(Parser parser, Set<Integer> preferredRules, Set<Integer> ignoredTokens)
    {
        this.parser = parser;
        this.atn = parser.getATN();
        this.vocabulary = parser.getVocabulary();
        this.ruleNames = parser.getRuleNames();
        if (preferredRules != null)
        {
            this.preferredRules = preferredRules;
        }
        if (ignoredTokens != null)
        {
            this.ignoredTokens = ignoredTokens;
        }
    }

    public Set<Integer> getPreferredRules()
    {
        return Collections.unmodifiableSet(preferredRules);
    }

    public void setPreferredRules(Set<Integer> preferredRules)
    {
        this.preferredRules = new HashSet<>(preferredRules);
    }

    /**
     * Main entry point. The caretTokenIndex specifies the token stream index for the token which currently covers the caret (or any other position for which you want code completion candidates).
     *
     * <p>
     * Optionally pass a parser rule context to limit the ATN walk to only that rule and its sub-rules. This can significantly speed up retrieval but might miss candidates outside the given context.
     */
    public CandidatesCollection collectCandidates(int caretTokenIndex, ParserRuleContext context)
    {
        this.shortcutMap.clear();
        this.candidates.rules.clear();
        this.candidates.tokens.clear();
        this.statesProcessed = 0;
        this.precedenceStack = new ArrayList<>();

        this.tokenStartIndex = context != null ? context.start.getTokenIndex()
                : 0;
        TokenStream tokenStream = this.parser.getInputStream();
        int currentIndex = tokenStream.index();
        tokenStream.seek(this.tokenStartIndex);
        this.tokens = new LinkedList<>();
        int offset = 1;
        while (true)
        {
            Token token = tokenStream.LT(offset++);
            this.tokens.add(token);
            if (token.getTokenIndex() >= caretTokenIndex
                    || token.getType() == Token.EOF)
            {
                break;
            }
        }
        tokenStream.seek(currentIndex);

        LinkedList<Integer> callStack = new LinkedList<>();
        int startRule = context != null ? context.getRuleIndex()
                : 0;
        if (startRule < 0)
        {
            startRule = 0;
        }
        this.processRule(this.atn.ruleToStartState[startRule], 0, callStack, "\n", 0);

        tokenStream.seek(currentIndex);

        // Post-process rule candidates: find the last (right-most) occurrence of each preferred
        // rule and extract its start/end offsets in the input stream.
        for (int ruleId : preferredRules)
        {
            final Map<Integer, Set<Integer>> shortcut = shortcutMap.get(ruleId);
            if (shortcut == null
                    || shortcut.isEmpty())
            {
                continue;
            }
            final int startToken = Collections.max(shortcut.keySet());
            final Set<Integer> endSet = shortcut.get(startToken);
            final int endToken;
            if (endSet.isEmpty())
            {
                endToken = tokens.size() - 1;
            }
            else
            {
                endToken = Collections.max(shortcut.get(startToken));
            }
            final int startOffset = tokens.get(startToken)
                    .getStartIndex();
            final int endOffset;
            if (tokens.get(endToken)
                    .getType() == Token.EOF)
            {
                endOffset = tokens.get(endToken)
                        .getStartIndex();
            }
            else
            {
                endOffset = tokens.get(endToken - 1)
                        .getStopIndex() + 1;
            }

            final List<Integer> ruleStartStop = Arrays.asList(startOffset, endOffset);
            candidates.rulePositions.put(ruleId, ruleStartStop);
        }

        if (this.showResult
                && logger.isLoggable(Level.FINE))
        {
            StringBuilder logMessage = new StringBuilder();

            logMessage.append("States processed: ")
                    .append(this.statesProcessed)
                    .append("\n");
            logMessage.append("Collected rules:\n");
            for (Map.Entry<Integer, List<Integer>> entry : this.candidates.rules.entrySet())
            {
                logMessage.append("  ")
                        .append(entry.getKey())
                        .append(", path: ");
                for (Integer token : entry.getValue())
                {
                    logMessage.append(this.ruleNames[token])
                            .append(" ");
                }
                logMessage.append("\n");
            }

            logMessage.append("Collected Tokens:\n");
            for (Map.Entry<Integer, List<Integer>> entry : this.candidates.tokens.entrySet())
            {
                logMessage.append("  ")
                        .append(this.vocabulary.getDisplayName(entry.getKey()));
                for (Integer following : entry.getValue())
                {
                    logMessage.append(" ")
                            .append(this.vocabulary.getDisplayName(following));
                }
                logMessage.append("\n");
            }
            logger.log(Level.FINE, logMessage.toString());
        }

        return this.candidates;
    }

    /**
     * Check if the predicate associated with the given transition evaluates to true.
     */
    private boolean checkPredicate(PredicateTransition transition)
    {
        return transition.getPredicate()
                .eval(this.parser, ParserRuleContext.EMPTY);
    }

    /**
     * Walks the rule chain upwards to see if that matches any of the preferred rules. If found, that rule is added to the collection candidates and true is returned.
     */
    private boolean translateToRuleIndex(List<Integer> ruleStack)
    {
        if (this.preferredRules.isEmpty())
        {
            return false;
        }

        for (int i = 0; i < ruleStack.size(); ++i)
        {
            if (this.preferredRules.contains(ruleStack.get(i)))
            {
                List<Integer> path = new LinkedList<>(ruleStack.subList(0, i));
                boolean addNew = true;
                for (Map.Entry<Integer, List<Integer>> entry : this.candidates.rules.entrySet())
                {
                    if (!entry.getKey()
                            .equals(ruleStack.get(i))
                            || entry.getValue()
                                    .size() != path.size())
                    {
                        continue;
                    }
                    if (path.equals(entry.getValue()))
                    {
                        addNew = false;
                        break;
                    }
                }

                if (addNew)
                {
                    this.candidates.rules.put(ruleStack.get(i), path);
                    if (showDebugOutput
                            && logger.isLoggable(Level.FINE))
                    {
                        // FIX: was ruleNames[i] (loop index) — should be ruleNames[ruleStack.get(i)] (rule id)
                        logger.fine("=====> collected: " + this.ruleNames[ruleStack.get(i)]);
                    }
                }
                return true;
            }
        }

        return false;
    }

    /**
     * Follows the given transition and collects all symbols within the same rule that directly follow it without intermediate transitions to other rules, and only if there is a single symbol for a
     * transition.
     */
    private List<Integer> getFollowingTokens(Transition initialTransition)
    {
        LinkedList<Integer> result = new LinkedList<>();
        // FIX: was LinkedList — replaced with ArrayDeque for O(1) add/remove
        Deque<ATNState> pipeline = new ArrayDeque<>();
        pipeline.add(initialTransition.target);

        while (!pipeline.isEmpty())
        {
            ATNState state = pipeline.removeLast();

            for (Transition transition : state.getTransitions())
            {
                if (transition.getSerializationType() == Transition.ATOM)
                {
                    if (!transition.isEpsilon())
                    {
                        List<Integer> list = transition.label()
                                .toList();
                        if (list.size() == 1
                                && !this.ignoredTokens.contains(list.get(0)))
                        {
                            result.addLast(list.get(0));
                            pipeline.addLast(transition.target);
                        }
                    }
                    else
                    {
                        pipeline.addLast(transition.target);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Entry point for the recursive follow-set collection function. Returns a fully-populated FollowSetsHolder including the isExhaustive flag.
     */
    private FollowSetsHolder determineFollowSets(ATNState start, ATNState stop)
    {
        LinkedList<FollowSetWithPath> result = new LinkedList<>();
        // Path-scoped state set: add before recursing, remove on return, so the same ATNState can be
        // reached via different paths (only prevents cycles on the current path).
        Set<ATNState> stateStack = new HashSet<>();
        LinkedList<Integer> ruleStack = new LinkedList<>();
        // FIX: companion set for O(1) cycle detection (replaces LinkedList.indexOf() which is O(n))
        Set<Integer> ruleStackSet = new HashSet<>();

        boolean isExhaustive = this.collectFollowSets(start, stop, result, stateStack, ruleStack, ruleStackSet);

        IntervalSet combined = new IntervalSet();
        for (FollowSetWithPath set : result)
        {
            combined.addAll(set.intervals);
        }

        FollowSetsHolder holder = new FollowSetsHolder();
        holder.sets = result;
        holder.combined = combined;
        holder.isExhaustive = isExhaustive;
        return holder;
    }

    /**
     * Collects possible tokens which could be matched following the given ATN state. Essentially the same algorithm as LL1Analyzer but predicates are considered and no parser rule context is used.
     *
     * <p>
     * Returns true when the follow set is exhaustive (all paths were fully explored), false when at least one path reached a RULE_STOP state, meaning the caller may contribute additional tokens.
     */
    private boolean collectFollowSets(ATNState s, ATNState stopState, LinkedList<FollowSetWithPath> followSets, Set<ATNState> stateStack, LinkedList<Integer> ruleStack, Set<Integer> ruleStackSet)
    {
        // Cycle on the current path — treat as exhaustive for this branch.
        if (stateStack.contains(s))
        {
            return true;
        }
        stateStack.add(s);

        if (s.equals(stopState)
                || s.getStateType() == ATNState.RULE_STOP)
        {
            // Reached the end of a rule without covering all possible tokens; callers may add more.
            stateStack.remove(s);
            return false;
        }

        boolean isExhaustive = true;

        for (Transition transition : s.getTransitions())
        {
            if (transition.getSerializationType() == Transition.RULE)
            {
                RuleTransition ruleTransition = (RuleTransition) transition;
                // FIX: was ruleStack.indexOf(...) != -1 — O(n); now O(1) via companion set
                if (ruleStackSet.contains(ruleTransition.target.ruleIndex))
                {
                    continue;
                }
                ruleStack.addLast(ruleTransition.target.ruleIndex);
                ruleStackSet.add(ruleTransition.target.ruleIndex);
                boolean ruleExhaustive = this.collectFollowSets(transition.target, stopState, followSets, stateStack, ruleStack, ruleStackSet);
                ruleStack.removeLast();
                ruleStackSet.remove(ruleTransition.target.ruleIndex);

                // When the called rule is not exhaustive it can terminate early (RULE_STOP), so tokens
                // that follow the rule in the caller's context are also valid completions.
                if (!ruleExhaustive)
                {
                    boolean followExhaustive = this.collectFollowSets(ruleTransition.followState, stopState, followSets, stateStack, ruleStack, ruleStackSet);
                    isExhaustive &= followExhaustive;
                }
            }
            else if (transition.getSerializationType() == Transition.PREDICATE)
            {
                if (this.checkPredicate((PredicateTransition) transition))
                {
                    boolean branchExhaustive = this.collectFollowSets(transition.target, stopState, followSets, stateStack, ruleStack, ruleStackSet);
                    isExhaustive &= branchExhaustive;
                }
            }
            else if (transition.isEpsilon())
            {
                boolean branchExhaustive = this.collectFollowSets(transition.target, stopState, followSets, stateStack, ruleStack, ruleStackSet);
                isExhaustive &= branchExhaustive;
            }
            else if (transition.getSerializationType() == Transition.WILDCARD)
            {
                FollowSetWithPath set = new FollowSetWithPath();
                set.intervals = IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType);
                set.path = new LinkedList<Integer>(ruleStack);
                followSets.addLast(set);
            }
            else
            {
                IntervalSet label = transition.label();
                if (label != null
                        && label.size() > 0)
                {
                    if (transition.getSerializationType() == Transition.NOT_SET)
                    {
                        label = label.complement(IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType));
                    }
                    FollowSetWithPath set = new FollowSetWithPath();
                    set.intervals = label;
                    set.path = new LinkedList<Integer>(ruleStack);
                    set.following = this.getFollowingTokens(transition);
                    followSets.addLast(set);
                }
            }
        }

        stateStack.remove(s);
        return isExhaustive;
    }

    /**
     * Walks the ATN for a single rule only. Returns the token stream position for each path that could be matched in this rule. The result can be empty if only non-epsilon transitions didn't match
     * the current input, or if the caret position was hit.
     *
     * @param precedence the operator precedence level passed in by the caller for left-recursive rules (0 for non-recursive entry points).
     */
    private Set<Integer> processRule(ATNState startState, int tokenIndex, LinkedList<Integer> callStack, String indentation, int precedence)
    {
        // Check first if we've taken this path with the same input before.
        Map<Integer, Set<Integer>> positionMap = this.shortcutMap.get(startState.ruleIndex);
        if (positionMap == null)
        {
            positionMap = new HashMap<>();
            this.shortcutMap.put(startState.ruleIndex, positionMap);
        }
        else
        {
            if (positionMap.containsKey(tokenIndex))
            {
                if (showDebugOutput)
                {
                    logger.fine("=====> shortcut");
                }
                return positionMap.get(tokenIndex);
            }
        }

        Set<Integer> result = new HashSet<>();

        // For rule start states determine and cache the follow set, which gives 3 advantages:
        // 1) Quick check whether a symbol would be matched when following that rule.
        // 2) All collectable symbols are already together when at the caret entering a rule.
        // 3) This lookup is free on any 2nd or further visit of the same rule (common with recursive grammars).
        Map<Integer, FollowSetsHolder> setsPerState = followSetsByATN.get(this.parser.getClass()
                .getName());
        if (setsPerState == null)
        {
            // ConcurrentHashMap.computeIfAbsent is atomic — safe under concurrent access
            setsPerState = followSetsByATN.computeIfAbsent(this.parser.getClass()
                    .getName(), k -> new ConcurrentHashMap<>());
        }

        FollowSetsHolder followSets = setsPerState.get(startState.stateNumber);
        if (followSets == null)
        {
            RuleStopState stop = this.atn.ruleToStopState[startState.ruleIndex];
            followSets = this.determineFollowSets(startState, stop);
            setsPerState.put(startState.stateNumber, followSets);
        }

        // Track precedence for left-recursive rules so PRECEDENCE transitions are evaluated correctly.
        RuleStartState ruleStartState = (RuleStartState) startState;
        if (ruleStartState.isLeftRecursiveRule)
        {
            precedenceStack.add(precedence);
        }

        callStack.addLast(startState.ruleIndex);
        int currentSymbol = this.tokens.get(tokenIndex)
                .getType();

        if (tokenIndex >= this.tokens.size() - 1)
        { // At caret?
            if (this.preferredRules.contains(startState.ruleIndex))
            {
                this.translateToRuleIndex(callStack);
            }
            else
            {
                for (FollowSetWithPath set : followSets.sets)
                {
                    LinkedList<Integer> fullPath = new LinkedList<>(callStack);
                    fullPath.addAll(set.path);
                    if (!this.translateToRuleIndex(fullPath))
                    {
                        for (int symbol : set.intervals.toList())
                        {
                            if (!this.ignoredTokens.contains(symbol))
                            {
                                if (showDebugOutput
                                        && logger.isLoggable(Level.FINE))
                                {
                                    logger.fine("=====> collected: " + this.vocabulary.getDisplayName(symbol));
                                }
                                if (!this.candidates.tokens.containsKey(symbol))
                                {
                                    this.candidates.tokens.put(symbol, set.following);
                                }
                                else
                                {
                                    // Merge: keep the longest common prefix of the two following sequences.
                                    this.candidates.tokens.put(symbol, longestCommonPrefix(set.following, this.candidates.tokens.get(symbol)));
                                }
                            }
                            else
                            {
                                logger.fine("====> collection: Ignoring token: " + symbol);
                            }
                        }
                    }
                }
            }

            callStack.removeLast();
            if (ruleStartState.isLeftRecursiveRule)
            {
                precedenceStack.remove(precedenceStack.size() - 1);
            }
            return result;

        }
        else
        {
            // Process the rule if we either could pass it without consuming anything (non-exhaustive set
            // means there may be additional tokens contributed by callers) or if the current input symbol
            // will be matched somewhere after this entry point.
            if (followSets.isExhaustive
                    && !followSets.combined.contains(currentSymbol))
            {
                callStack.removeLast();
                if (ruleStartState.isLeftRecursiveRule)
                {
                    precedenceStack.remove(precedenceStack.size() - 1);
                }
                return result;
            }
        }

        // FIX: was LinkedList — replaced with ArrayDeque for O(1) addLast/removeLast
        Deque<PipelineEntry> statePipeline = new ArrayDeque<>();
        PipelineEntry currentEntry;

        statePipeline.add(new PipelineEntry(startState, tokenIndex));

        while (!statePipeline.isEmpty())
        {
            currentEntry = statePipeline.removeLast();
            ++this.statesProcessed;

            currentSymbol = this.tokens.get(currentEntry.tokenIndex)
                    .getType();

            boolean atCaret = currentEntry.tokenIndex >= this.tokens.size() - 1;
            if (logger.isLoggable(Level.FINE))
            {
                printDescription(indentation, currentEntry.state, this.generateBaseDescription(currentEntry.state), currentEntry.tokenIndex);
                if (this.showRuleStack)
                {
                    printRuleState(callStack);
                }
            }

            switch (currentEntry.state.getStateType())
            {
                case ATNState.RULE_START:
                    indentation += "  ";
                    break;

                case ATNState.RULE_STOP:
                {
                    result.add(currentEntry.tokenIndex);
                    continue;
                }

                default:
                    break;
            }

            Transition[] transitions = currentEntry.state.getTransitions();
            for (Transition transition : transitions)
            {
                switch (transition.getSerializationType())
                {
                    case Transition.RULE:
                    {
                        Set<Integer> endStatus = this.processRule(transition.target, currentEntry.tokenIndex, callStack, indentation, ((RuleTransition) transition).precedence);
                        for (Integer position : endStatus)
                        {
                            statePipeline.addLast(new PipelineEntry(((RuleTransition) transition).followState, position));
                        }
                        break;
                    }

                    case Transition.PREDICATE:
                    {
                        if (this.checkPredicate((PredicateTransition) transition))
                        {
                            statePipeline.addLast(new PipelineEntry(transition.target, currentEntry.tokenIndex));
                        }
                        break;
                    }

                    case Transition.PRECEDENCE:
                    {
                        // Left-recursive rule: only follow this transition if the operator precedence is
                        // at least as high as the level recorded when we entered this rule invocation.
                        PrecedencePredicateTransition predTransition = (PrecedencePredicateTransition) transition;
                        if (!precedenceStack.isEmpty()
                                && predTransition.precedence >= precedenceStack.get(precedenceStack.size() - 1))
                        {
                            statePipeline.addLast(new PipelineEntry(transition.target, currentEntry.tokenIndex));
                        }
                        break;
                    }

                    case Transition.WILDCARD:
                    {
                        if (atCaret)
                        {
                            if (!this.translateToRuleIndex(callStack))
                            {
                                for (Integer token : IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType)
                                        .toList())
                                {
                                    if (!this.ignoredTokens.contains(token))
                                    {
                                        this.candidates.tokens.put(token, new LinkedList<Integer>());
                                    }
                                }
                            }
                        }
                        else
                        {
                            statePipeline.addLast(new PipelineEntry(transition.target, currentEntry.tokenIndex + 1));
                        }
                        break;
                    }

                    default:
                    {
                        if (transition.isEpsilon())
                        {
                            if (atCaret)
                            {
                                this.translateToRuleIndex(callStack);
                            }
                            statePipeline.addLast(new PipelineEntry(transition.target, currentEntry.tokenIndex));
                            continue;
                        }

                        IntervalSet set = transition.label();
                        if (set != null
                                && set.size() > 0)
                        {
                            if (transition.getSerializationType() == Transition.NOT_SET)
                            {
                                set = set.complement(IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType));
                            }
                            if (atCaret)
                            {
                                if (!this.translateToRuleIndex(callStack))
                                {
                                    List<Integer> list = set.toList();
                                    boolean addFollowing = list.size() == 1;
                                    for (Integer symbol : list)
                                    {
                                        if (!this.ignoredTokens.contains(symbol))
                                        {
                                            if (showDebugOutput
                                                    && logger.isLoggable(Level.FINE))
                                            {
                                                logger.fine("=====> collected: " + this.vocabulary.getDisplayName(symbol));
                                            }
                                            List<Integer> following = addFollowing ? this.getFollowingTokens(transition)
                                                    : new LinkedList<>();
                                            if (!this.candidates.tokens.containsKey(symbol))
                                            {
                                                this.candidates.tokens.put(symbol, following);
                                            }
                                            else
                                            {
                                                // Merge: keep the longest common prefix of the two following sequences.
                                                this.candidates.tokens.put(symbol, longestCommonPrefix(following, this.candidates.tokens.get(symbol)));
                                            }
                                        }
                                        else
                                        {
                                            logger.fine("====> collected: Ignoring token: " + symbol);
                                        }
                                    }
                                }
                            }
                            else
                            {
                                if (set.contains(currentSymbol))
                                {
                                    if (showDebugOutput
                                            && logger.isLoggable(Level.FINE))
                                    {
                                        logger.fine("=====> consumed: " + this.vocabulary.getDisplayName(currentSymbol));
                                    }
                                    statePipeline.addLast(new PipelineEntry(transition.target, currentEntry.tokenIndex + 1));
                                }
                            }
                        }
                    }
                }
            }
        }

        callStack.removeLast();
        if (ruleStartState.isLeftRecursiveRule)
        {
            precedenceStack.remove(precedenceStack.size() - 1);
        }

        positionMap.put(tokenIndex, result);

        return result;
    }

    /**
     * Returns the longest common prefix of two token-following sequences. Used when the same candidate token is reachable via multiple paths with different continuations: rather than discarding all
     * following tokens on conflict, we keep what both paths agree on.
     */
    private static List<Integer> longestCommonPrefix(List<Integer> a, List<Integer> b)
    {
        int minLength = Math.min(a.size(), b.size());
        List<Integer> result = new LinkedList<>();
        for (int i = 0; i < minLength; i++)
        {
            if (a.get(i)
                    .equals(b.get(i)))
            {
                result.add(a.get(i));
            }
            else
            {
                break;
            }
        }
        return result;
    }

    private String[] atnStateTypeMap = new String[] {
            "invalid", "basic", "rule start", "block start", "plus block start", "star block start", "token start", "rule stop", "block end", "star loop back", "star loop entry", "plus loop back",
            "loop end" };

    private String generateBaseDescription(ATNState state)
    {
        String stateValue = (state.stateNumber == ATNState.INVALID_STATE_NUMBER) ? "Invalid"
                : Integer.toString(state.stateNumber);
        return "[" + stateValue + " " + this.atnStateTypeMap[state.getStateType()] + "] in " + this.ruleNames[state.ruleIndex];
    }

    private void printDescription(String currentIndent, ATNState state, String baseDescription, int tokenIndex)
    {
        StringBuilder output = new StringBuilder(currentIndent);

        StringBuilder transitionDescription = new StringBuilder();
        if (this.debugOutputWithTransitions
                && logger.isLoggable(Level.FINER))
        {
            for (Transition transition : state.getTransitions())
            {
                StringBuilder labels = new StringBuilder();
                List<Integer> symbols = (transition.label() != null) ? transition.label()
                        .toList()
                        : new LinkedList<>();
                if (symbols.size() > 2)
                {
                    labels.append(this.vocabulary.getDisplayName(symbols.get(0)) + " .. " + this.vocabulary.getDisplayName(symbols.get(symbols.size() - 1)));
                }
                else
                {
                    for (Integer symbol : symbols)
                    {
                        if (labels.length() > 0)
                        {
                            labels.append(", ");
                        }
                        labels.append(this.vocabulary.getDisplayName(symbol));
                    }
                }
                if (labels.length() == 0)
                {
                    labels.append("ε");
                }
                transitionDescription.append("\n")
                        .append(currentIndent)
                        .append("\t(")
                        .append(labels)
                        .append(") [")
                        .append(transition.target.stateNumber)
                        .append(" ")
                        .append(this.atnStateTypeMap[transition.target.getStateType()])
                        .append("] in ")
                        .append(this.ruleNames[transition.target.ruleIndex]);
            }

            if (tokenIndex >= this.tokens.size() - 1)
            {
                output.append("<<")
                        .append(this.tokenStartIndex + tokenIndex)
                        .append(">> ");
            }
            else
            {
                output.append("<")
                        .append(this.tokenStartIndex + tokenIndex)
                        .append("> ");
            }
            logger.finer(output + "Current state: " + baseDescription + transitionDescription);
        }
    }

    private void printRuleState(LinkedList<Integer> stack)
    {
        if (stack.isEmpty())
        {
            logger.fine("<empty stack>");
            return;
        }

        if (logger.isLoggable(Level.FINER))
        {
            StringBuilder sb = new StringBuilder();
            for (Integer rule : stack)
            {
                sb.append("  ")
                        .append(this.ruleNames[rule])
                        .append("\n");
            }
            logger.log(Level.FINER, sb.toString());
        }
    }
}
