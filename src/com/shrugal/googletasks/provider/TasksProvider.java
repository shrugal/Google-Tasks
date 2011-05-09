package com.shrugal.googletasks.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

public class TasksProvider extends ContentProvider {
	
	//Uri stuff
    public static final String AUTHORITY = "com.shrugal.googletasks.provider";
    private static UriMatcher sUriMatcher;
	private static final int LISTS = 0;
	private static final int LIST_ID = 1;
	private static final int TASKS = 2;
	private static final int TASK_ID = 3;
	
	/* Members */
	private DatabaseHelper mDb;

	@Override
	public boolean onCreate() {
		mDb = new DatabaseHelper(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,	String order) {
		SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
		
		//Table, selection and order
		switch(sUriMatcher.match(uri)) {
			case LIST_ID:
				builder.appendWhere(Lists._ID +" = "+ uri.getPathSegments().get(1));
			case LISTS:
				builder.setTables(Lists.TABLE_NAME);
				order = TextUtils.isEmpty(order) ? Lists.DEFAULT_ORDER : order +", "+ Lists.DEFAULT_ORDER;
				break;
			case TASK_ID:
				builder.appendWhere(Tasks._ID +" = "+ uri.getPathSegments().get(1));
			case TASKS:
				builder.setTables(Tasks.TABLE_NAME);
				order = TextUtils.isEmpty(order) ? Tasks.DEFAULT_ORDER : order +", "+ Tasks.DEFAULT_ORDER;
				break;
			default:
				throw new IllegalArgumentException("Unknown URI "+ uri);
		}
		
		//Create cursor
		Cursor c = builder.query(mDb.getReadableDatabase(), projection, selection, selectionArgs, null, null, order);
		c.setNotificationUri(getContext().getContentResolver(), uri);
		
		return c;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		//TODO: Check fields
		long id = 0;
		Uri result;
		switch(sUriMatcher.match(uri)) {
			case LISTS:
				id = mDb.addList(values.getAsLong(Lists.G_ID),
						values.getAsString(Lists.NAME),
						values.getAsLong(Lists.LAST_MODIFIED),
						values.getAsInteger(Lists.LAST_MODIFIED_TYPE));
				result = ContentUris.withAppendedId(Lists.CONTENT_URI, id);
				break;
			case TASKS:
				id = mDb.addTask(values.getAsLong(Tasks.G_ID),
						values.getAsLong(Tasks.LIST_ID),
						values.getAsString(Tasks.NAME),
						values.getAsString(Tasks.NOTES),
						values.getAsLong(Tasks.LAST_MODIFIED),
						values.getAsInteger(Tasks.LAST_MODIFIED_TYPE),
						values.getAsBoolean(Tasks.COMPLETED));
				result = ContentUris.withAppendedId(Tasks.CONTENT_URI, id);
				break;
			default:
				throw new IllegalArgumentException("Unknown URI "+ uri);
		}
		
		if(id > 0) getContext().getContentResolver().notifyChange(result, null);
		return result;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		int count = 0;
		switch(sUriMatcher.match(uri)) {
			case LISTS:
				count = mDb.updateLists(selection,
						values.getAsLong(Lists.LAST_MODIFIED),
						values.getAsInteger(Lists.LAST_MODIFIED_TYPE),
						values.getAsBoolean(Lists.DELETED));
				break;
			case LIST_ID:
				count = mDb.updateList(Long.valueOf(uri.getPathSegments().get(1)),
						values.getAsLong(Lists.G_ID),
						values.getAsString(Lists.NAME),
						values.getAsLong(Lists.LAST_MODIFIED),
						values.getAsInteger(Lists.LAST_MODIFIED_TYPE),
						values.getAsBoolean(Lists.DELETED));
				break;
			case TASKS:
				count = mDb.updateTasks(selection,
						values.getAsLong(Tasks.LIST_ID),
						values.getAsString(Tasks.NOTES),
						values.getAsLong(Tasks.LAST_MODIFIED),
						values.getAsInteger(Tasks.LAST_MODIFIED_TYPE),
						values.getAsBoolean(Tasks.COMPLETED),
						values.getAsBoolean(Tasks.DELETED));
				break;
			case TASK_ID:
				count = mDb.updateTask(Long.valueOf(uri.getPathSegments().get(1)),
						values.getAsLong(Tasks.G_ID),
						values.getAsLong(Tasks.LIST_ID),
						values.getAsString(Tasks.NAME),
						values.getAsString(Tasks.NOTES),
						values.getAsLong(Tasks.LAST_MODIFIED),
						values.getAsInteger(Tasks.LAST_MODIFIED_TYPE),
						values.getAsBoolean(Tasks.COMPLETED),
						values.getAsBoolean(Tasks.DELETED),
						values.getAsInteger(Tasks.ORDER));
				break;
			default:
				throw new IllegalArgumentException("Unknown URI "+ uri);
		}

		if(count > 0) getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		int count = 0;
		switch(sUriMatcher.match(uri)) {
			case LISTS:
				count = mDb.removeLists(selection);
				break;
			case LIST_ID:
				count = mDb.removeList(Long.valueOf(uri.getPathSegments().get(1)));
				break;
			case TASKS:
				count = mDb.removeTasks(selection);
				break;
			case TASK_ID:
				count = mDb.removeTask(Long.valueOf(uri.getPathSegments().get(1)));
				break;
			default:
				throw new IllegalArgumentException("Unknown URI "+ uri);
		}

		if(count > 0) getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	@Override
	public String getType(Uri uri) {
		switch(sUriMatcher.match(uri)) {
			case LISTS:
				return Lists.CONTENT_TYPE;
			case LIST_ID:
				return Lists.CONTENT_TYPE_ITEM;
			case TASKS:
				return Tasks.CONTENT_TYPE;
			case TASK_ID:
				return Tasks.CONTENT_TYPE_ITEM;
			default:
				throw new IllegalArgumentException("Unknown URI "+ uri);
		}
	}
	
		
	/* ------------------------------------------------------------
	 *                      Database Helper
	 * ------------------------------------------------------------ */
	private class DatabaseHelper extends SQLiteOpenHelper {

		/* Finals */
		private static final int VERSION = 1;
		private static final String DB_NAME = "googletasks";
		
		//Create Querys
		private static final String LISTS_CREATE_QUERY = "CREATE TABLE " + Lists.TABLE_NAME + " (" +
			Lists._ID +" INTEGER PRIMARY KEY AUTOINCREMENT, " +
			Lists.G_ID +" INTEGER, " +
			Lists.NAME +" TEXT, " +
			Lists.LAST_MODIFIED +" INTEGER, " +
			Lists.LAST_MODIFIED_TYPE +" INTEGER, " +
			Lists.DELETED +" INTEGER, " +
			Lists.ORDER +" INTEGER" +
		")";
		private static final String TASKS_CREATE_QUERY = "CREATE TABLE " + Tasks.TABLE_NAME + " (" +
			Tasks._ID +" INTEGER PRIMARY KEY AUTOINCREMENT, " +
			Tasks.G_ID +" INTEGER, " +
			Tasks.LIST_ID +" INTEGER, " +
			Tasks.NAME +" TEXT, " +
			Tasks.NOTES +" TEXT, " +
			Tasks.DATE +" INTEGER, " +
			Tasks.LAST_MODIFIED +" INTEGER, " +
			Tasks.LAST_MODIFIED_TYPE +" INTEGER, " +
			Tasks.COMPLETED +" INTEGER, " +
			Tasks.DELETED +" INTEGER, " +
			Tasks.ORDER +" INTEGER" +
		")";
		
		public DatabaseHelper(Context context) {
			super(context, DB_NAME, null, VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(LISTS_CREATE_QUERY);
			db.execSQL(TASKS_CREATE_QUERY);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS "+ Lists.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS "+ Tasks.TABLE_NAME);
            onCreate(db);
		}
		
		/* ----------------------------------------------------------------
		 *                             Lists
		 * ----------------------------------------------------------------*/
		
		/**
		 * Add a list to the db
		 * @param g_id
		 * @param name
		 * @param last_modified
		 * @return
		 */
		public long addList(Long g_id, String name, Long last_modified, int last_modified_type) {
			ContentValues values = new ContentValues();
			if(g_id != null) values.put(Lists.G_ID, g_id);
			values.put(Lists.NAME, name);
			values.put(Lists.DELETED, 0);
			values.put(Lists.LAST_MODIFIED, (last_modified != null ? last_modified : System.currentTimeMillis()));
			values.put(Lists.LAST_MODIFIED_TYPE, (last_modified != null ? last_modified_type : Lists.LAST_MODIFIED_TYPE_LOCAL));			

			SQLiteDatabase db = getWritableDatabase();
			return db.insert(Lists.TABLE_NAME, null, values);
		}
		
		/**
		 * Update a list in the db
		 * @param id
		 * @param g_id
		 * @param name
		 * @param last_modified
		 * @param deleted
		 * @return
		 */
		public int updateList (long id, Long g_id, String name, Long last_modified, Integer last_modified_type, Boolean deleted) {	
			ContentValues values = new ContentValues();
			if(g_id != null) values.put(Lists.G_ID, g_id);
			if(name != null) values.put(Lists.NAME, name);
			if(deleted != null) values.put(Lists.DELETED, deleted ? 1 : 0);
			if(last_modified != null) values.put(Lists.LAST_MODIFIED, last_modified);
			if(last_modified_type != null) values.put(Lists.LAST_MODIFIED_TYPE, last_modified_type);

			SQLiteDatabase db = getWritableDatabase();
			return db.update(Lists.TABLE_NAME, values, Lists._ID+" = "+ id, null);
		}
		
		/**
		 * Update lists in the db by selection
		 * @param where
		 * @param last_modified
		 * @param deleted
		 * @return
		 */
		public int updateLists (String where, Long last_modified, Integer last_modified_type, Boolean deleted) {	
			ContentValues values = new ContentValues();
			if(deleted != null) values.put(Lists.DELETED, deleted ? 1 : 0);
			if(last_modified != null) values.put(Lists.LAST_MODIFIED, last_modified);
			if(last_modified_type != null) values.put(Lists.LAST_MODIFIED_TYPE, last_modified_type);

			SQLiteDatabase db = getWritableDatabase();
			return db.update(Lists.TABLE_NAME, values, where, null);
		}
		
		/**
		 * Remove a list from the db
		 * @param id
		 * @return
		 */
		public int removeList (long id) {
			SQLiteDatabase db = getWritableDatabase();
			
			db.delete(Tasks.TABLE_NAME, Tasks.LIST_ID +" = "+ id, null);		
			return db.delete(Lists.TABLE_NAME, Lists._ID +" = "+ id, null);
		}
		
		/**
		 * Remove a list from the db
		 * @param id
		 * @return
		 */
		public int removeLists (String where) {
			SQLiteDatabase db = getWritableDatabase();
			
			Cursor c = db.query(Lists.TABLE_NAME, new String[] {Lists._ID}, where, null, null, null, null);
			if(c.getCount() > 0) {
				//Remove tasks in these lists
				Long[] listIds = new Long[c.getCount()];
				for(int i=0; c.moveToNext(); i++) listIds[i] = c.getLong(c.getColumnIndex(Lists._ID)); 
				removeTasks(Tasks.LIST_ID +" IN ("+ TextUtils.join(", ", listIds) +")");
					
				return db.delete(Lists.TABLE_NAME, Lists._ID +" IN ("+ TextUtils.join(", ", listIds) +")", null);
			} else return 0;
		}		
		
		/* ----------------------------------------------------------------
		 *                             Tasks
		 * ----------------------------------------------------------------*/
		
		/**
		 * Add a new task to the db
		 * @param g_id
		 * @param list_id
		 * @param name
		 * @param notes
		 * @param last_modified
		 * @param completed
		 * @return
		 */
		public long addTask(Long g_id, long list_id, String name, String notes, Long last_modified, int last_modified_type, Boolean completed) {		
			ContentValues values = new ContentValues();
			
			//Relations
			if(g_id != null) values.put(Tasks.G_ID, g_id);
			values.put(Tasks.LIST_ID, list_id);
			
			//Attributes
			values.put(Tasks.NAME, name);
			values.put(Tasks.NOTES, notes);
			values.put(Tasks.LAST_MODIFIED, (last_modified != null ? last_modified : System.currentTimeMillis()));
			values.put(Tasks.LAST_MODIFIED_TYPE, (last_modified != null ? last_modified_type : Tasks.LAST_MODIFIED_TYPE_LOCAL));
			values.put(Tasks.COMPLETED, (completed != null && completed) ? 1 : 0);
			values.put(Tasks.DELETED, 0);
			
			//Order
			SQLiteDatabase db = getWritableDatabase();
			Cursor c = db.query(Tasks.TABLE_NAME, new String[] {"MAX("+ Tasks.ORDER +")"}, Tasks.LIST_ID +" = "+ list_id, null, Tasks.LIST_ID, null, null);
			values.put(Tasks.ORDER, c.moveToFirst() ? c.getInt(0) + 1 : 0);
			
			return db.insert(Tasks.TABLE_NAME, null, values);
		}
		
		/**
		 * Update a task in the db
		 * @param id
		 * @param g_id
		 * @param list_id
		 * @param name
		 * @param notes
		 * @param last_modified
		 * @param completed
		 * @param deleted
		 * @return the number of rows affected, typically 1 or 0 if something went wrong
		 */
		public int updateTask (long id, Long g_id, Long list_id, String name, String notes, Long last_modified, Integer last_modified_type, Boolean completed, Boolean deleted, Integer order) {	
			SQLiteDatabase db = getWritableDatabase();
			Cursor c = db.query(Tasks.TABLE_NAME, null, Tasks._ID +" = "+ id, null, null, null, null);
			if(!c.moveToFirst()) return 0;
			ContentValues values = new ContentValues();
			
			//Relations
			if(g_id != null) values.put(Tasks.G_ID, g_id);
			if(list_id != null) values.put(Tasks.LIST_ID, list_id);
			
			//Attributes
			if(name != null) values.put(Tasks.NAME, name);
			if(notes != null) values.put(Tasks.NOTES, notes);
			if(completed != null) values.put(Tasks.COMPLETED, completed ? 1 : 0);
			if(deleted != null) values.put(Tasks.DELETED, deleted ? 1 : 0);
			if(last_modified != null) values.put(Tasks.LAST_MODIFIED, last_modified);
			if(last_modified_type != null) values.put(Tasks.LAST_MODIFIED_TYPE, last_modified_type);
			
			//Order
			if(order != null) {
				int oldOrder = c.getInt(c.getColumnIndex(Tasks.ORDER));
				int oldListId = c.getInt(c.getColumnIndex(Tasks.LIST_ID));
				
				//Order or list changed
				if(order != oldOrder || list_id != oldListId) {
					ContentValues updateOrderValues = new ContentValues();
					
					//Update source list
					updateOrderValues.put(Tasks.ORDER, Tasks.ORDER +" - 1");
					db.update(Tasks.TABLE_NAME, values, Tasks.LIST_ID +" = "+ oldListId +" AND "+ Tasks.ORDER +" > "+ oldOrder, null);
					
					//Update destination list
					updateOrderValues.put(Tasks.ORDER, Tasks.ORDER +" + 1");
					db.update(Tasks.TABLE_NAME, values, Tasks.LIST_ID +" = "+ list_id +" AND "+ Tasks.ORDER +" > "+ order, null);
				}
			}
			
			c.close();
			return db.update(Tasks.TABLE_NAME, values, Tasks._ID +" = "+ id, null);
		}
		
		/**
		 * Update multiple tasks in the db
		 * @param where
		 * @param list_id
		 * @param notes
		 * @param last_modified
		 * @param completed
		 * @param deleted
		 * @return
		 */
		public int updateTasks (String where, Long list_id, String notes, Long last_modified, Integer last_modified_type, Boolean completed, Boolean deleted) {	
			SQLiteDatabase db = getWritableDatabase();
			ContentValues values = new ContentValues();
			
			//Relations
			if(list_id != null) values.put(Tasks.LIST_ID, list_id);
			
			//Attributes
			if(notes != null) values.put(Tasks.NOTES, notes);
			if(completed != null) values.put(Tasks.COMPLETED, completed ? 1 : 0);
			if(deleted != null) values.put(Tasks.DELETED, deleted ? 1 : 0);
			if(last_modified != null) values.put(Tasks.LAST_MODIFIED, last_modified);
			if(last_modified_type != null) values.put(Tasks.LAST_MODIFIED_TYPE, last_modified_type);
			
			//Order

			return db.update(Tasks.TABLE_NAME, values, where, null);
		}
		
		/**
		 * Remove a task from the db
		 * @param id
		 * @return
		 */
		public int removeTask (long id) {
			SQLiteDatabase db = getWritableDatabase();
			return db.delete(Tasks.TABLE_NAME, Tasks._ID +" = "+ id, null);
		}
		
		/**
		 * Remove a task from the db
		 * @param id
		 * @return
		 */
		public int removeTasks (String where) {
			SQLiteDatabase db = getWritableDatabase();
			return db.delete(Tasks.TABLE_NAME, where, null);
		}
	}

	/* ------------------------------------------------------------
	 *                        Uri Matcher
	 * ------------------------------------------------------------ */
	static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, "lists", LISTS);
        sUriMatcher.addURI(AUTHORITY, "lists/#", LIST_ID);
        sUriMatcher.addURI(AUTHORITY, "tasks", TASKS);
        sUriMatcher.addURI(AUTHORITY, "tasks/#", TASK_ID);
	}
}
