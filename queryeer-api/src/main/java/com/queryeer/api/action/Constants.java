package com.queryeer.api.action;

import javax.swing.Action;

/** Constants used in actions */
public interface Constants
{
    /**
     * Key used in actions to store sub actions used when a sub menu should be shown in popups. The value stored for this key should be a {@link java.util.List} with {@link Action}'s
     */
    public static final String SUB_ACTIONS = "subActions";

    /** Key stored on components to signal to top Queryeer window that actions are available in menus/toolbar */
    public static final String QUERYEER_ACTIONS = "QueryeerActions";

    /** Should action be shown in toolbar */
    public static final String ACTION_SHOW_IN_TOOLBAR = "showInToolBar";
    /** Should action be a toggle button */
    public static final String ACTION_TOGGLE = "toggleAction";
    /** Order where action should be placed */
    public static final String ACTION_ORDER = "actionOrder";
    /** Should action be shown in menu */
    public static final String ACTION_SHOW_IN_MENU = "showInMenu";
    /** If action should be shown in menu then this property specifies which menu */
    public static final String ACTION_MENU = "menu";

    /** Edit menu */
    public static final String EDIT_MENU = "edit";
}
