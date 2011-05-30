package com.shrugal.googletasks.provider;

import android.net.Uri;
import android.provider.BaseColumns;

public final class Tasks implements BaseColumns {

	//Uri stuff
	public static final Uri CONTENT_URI = Uri.parse("content://"+ TasksProvider.AUTHORITY +"/tasks");
	public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.shrugal.task";
	public static final String CONTENT_TYPE_ITEM = "vnd.android.cursor.item/vnd.shrugal.task";
	
	//Database table name
	public static final String TABLE_NAME = "tasks";
	
	//Database table keys
	public static final String G_ID = "g_id";
	public static final String LIST_ID = "list_id";
	public static final String PARENT = "parent";
	public static final String NAME = "name";
	public static final String NOTES = "notes";
	public static final String DATE = "date";
	public static final String LAST_MODIFIED = "last_modified";
	public static final String LAST_MODIFIED_TYPE = "last_modified_local";
	public static final String COMPLETED = "completed";
	public static final String DELETED = "deleted";
	public static final String LEFT = "lft";
	public static final String RIGHT = "rgt";
	
	public static final String[] KEYS = {_ID, G_ID, LIST_ID, PARENT, NAME, NOTES, DATE, LAST_MODIFIED_TYPE, LAST_MODIFIED, COMPLETED, DELETED, LEFT, RIGHT};

	//Defaults
	public static final String DEFAULT_ORDER = LIST_ID +" ASC, "+ LEFT +" ASC";
	public static final int LAST_MODIFIED_TYPE_LOCAL = 0;
	public static final int LAST_MODIFIED_TYPE_SERVER = 1;
	
	//Other
	public static final String PREVIOUS = "previous";
	public static final String RANK = "rank";
	public static final String CHILDS = "childs";
}
