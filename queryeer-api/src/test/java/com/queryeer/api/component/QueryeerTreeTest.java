package com.queryeer.api.component;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JToggleButton;

import com.queryeer.api.component.QueryeerTree.FilterPath;
import com.queryeer.api.component.QueryeerTree.QueryeerTreeModel;
import com.queryeer.api.component.QueryeerTree.RegularNode;

/** Tree tester. */
public class QueryeerTreeTest extends JFrame
{

    QueryeerTreeTest()
    {
        QueryeerTreeModel model = new QueryeerTreeModel(new Node("root", 0));
        QueryeerTree tree = new QueryeerTree(model);

        getContentPane().setLayout(new BorderLayout());

        getContentPane().add(tree, BorderLayout.CENTER);

        JToggleButton filter = new JToggleButton("Filter");
        filter.addActionListener(l ->
        {
            if (filter.isSelected())
            {
                model.setFilterPaths(List.of(new FilterPath(new RegularNode[] { new Node("child1", 1), new Node("child1", 2) }, false),
                        new FilterPath(new RegularNode[] { new Node("child2", 1), new Node("child2", 2), new Node("child1", 3) }, true)));
            }
            else
            {
                model.setFilterPaths(null);
            }
        });
        getContentPane().add(filter, BorderLayout.NORTH);

        setPreferredSize(new Dimension(800, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
    }

    public static void main(String[] args)
    {
        QueryeerTreeTest test = new QueryeerTreeTest();
        test.setVisible(true);
    }

    static class Node implements RegularNode
    {
        private String title;
        private int level;

        Node(String title, int level)
        {
            this.title = title;
            this.level = level;
        }

        @Override
        public String getTitle()
        {
            return title + "( " + level + " )";
        }

        @Override
        public boolean isLeaf()
        {
            return false;
        }

        @Override
        public List<RegularNode> loadChildren()
        {
            return List.of(new Node("child1", level + 1), new Node("child2", level + 1));
        }

        @Override
        public boolean equals(Object obj)
        {
            return obj instanceof Node n
                    && title.equals(n.title)
                    && level == n.level;
        }

        @Override
        public String toString()
        {
            return getTitle();
        }
    }
}
