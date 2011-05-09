package com.shrugal.googletasks.provider;

import android.net.Uri;
import android.provider.BaseColumns;

public final class Lists implements BaseColumns {

	//Uri stuff
	public static final Uri CONTENT_URI = Uri.parse("content://" +TasksProvider.AUTHORITY +"/lists");
	public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.shrugal.list";
	public static final String CONTENT_TYPE_ITEM = "vnd.android.cursor.item/vnd.shrugal.list";

	//Database table name
	public static final String TABLE_NAME = "lists";
	
	//Database table keys
	public static final String G_ID = "g_id";
	public static final String NAME = "name";
	public static final String LAST_MODIFIED = "last_modified";
	public static final String LAST_MODIFIED_TYPE = "last_modified_local";
	public static final String DELETED = "deleted";
	public static final String ORDER = "sort";
	
	//Defaults
	public static final String DEFAULT_ORDER = NAME +" ASC";
	public static final int LAST_MODIFIED_TYPE_LOCAL = 0;
	public static final int LAST_MODIFIED_TYPE_SERVER = 1;
}
