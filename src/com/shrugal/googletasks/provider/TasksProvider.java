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
				id = mDb.addList(values);
				result = ContentUris.withAppendedId(Lists.CONTENT_URI, id);
				break;
			case TASKS:
				id = mDb.addTask(values);
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
				count = mDb.updateLists(values, selection);
				break;
			case LIST_ID:
				count = mDb.updateList(Long.valueOf(uri.getPathSegments().get(1)), values);
				break;
			case TASKS:
				count = mDb.updateTasks(values, selection);
				break;
			case TASK_ID:
				count = mDb.updateTask(Long.valueOf(uri.getPathSegments().get(1)), values);
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
			Tasks.PARENT +" INTEGER" +
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
		 * @param last_modified_type
		 * @return
		 */
		public long addList(ContentValues values) {			
			//Don't change id
			values.remove(Lists._ID);
			
			//Deleted
			values.put(Lists.DELETED, 0);

			//Last modified
			if(values.getAsLong(Lists.LAST_MODIFIED) == null) {
				values.put(Lists.LAST_MODIFIED, System.currentTimeMillis());
			}
			
			//Last modified type
			Integer last_modified_type = values.getAsInteger(Lists.LAST_MODIFIED_TYPE);
			if(last_modified_type == null) {
				values.put(Lists.LAST_MODIFIED_TYPE, Lists.LAST_MODIFIED_TYPE_LOCAL);
			} else if(last_modified_type != Lists.LAST_MODIFIED_TYPE_LOCAL && last_modified_type != Lists.LAST_MODIFIED_TYPE_SERVER) return -1;

			SQLiteDatabase db = getWritableDatabase();
			return db.insert(Lists.TABLE_NAME, null, values);
		}
		
		/**
		 * Update a list in the db
		 * @param id
		 * @param values
		 * @return
		 */
		public int updateList (Long id, ContentValues values) {
			Integer deleted, last_modified_type;
			Boolean flag;
			
			//Don't change id
			values.remove(Lists._ID);
			
			//Deleted
			deleted = values.getAsInteger(Lists.DELETED);
			if(deleted != 0 && deleted != 1) {
				if((flag = values.getAsBoolean(Lists.DELETED)) != null) values.put(Lists.DELETED, flag ? 1 : 0);
				else values.remove(Lists.DELETED);
			}
			
			//Last modified
			last_modified_type = values.getAsInteger(Lists.LAST_MODIFIED_TYPE);
			if(last_modified_type != null && last_modified_type != Lists.LAST_MODIFIED_TYPE_LOCAL && last_modified_type != Tasks.LAST_MODIFIED_TYPE_SERVER) {
				values.remove(Lists.LAST_MODIFIED);
				values.remove(Lists.LAST_MODIFIED_TYPE);
			}

			SQLiteDatabase db = getWritableDatabase();
			return db.update(Lists.TABLE_NAME, values, Lists._ID+" = "+ id, null);
		}
		
		/**
		 * Update lists in the db by selection
		 * @param values
		 * @param selection
		 * @return
		 */
		public int updateLists (ContentValues values, String selection) {
			Boolean flag;
			Integer deleted, last_modified_type;			

			//Don't change ...
			values.remove(Lists._ID);
			values.remove(Lists.ORDER);
			values.remove(Lists.NAME);
			values.remove(Lists.G_ID);
			
			//Deleted
			deleted = values.getAsInteger(Lists.DELETED);
			if(deleted != 0 && deleted != 1) {
				if((flag = values.getAsBoolean(Lists.DELETED)) != null) values.put(Lists.DELETED, flag ? 1 : 0);
				else values.remove(Lists.DELETED);
			}
			
			//Last modified
			last_modified_type = values.getAsInteger(Lists.LAST_MODIFIED_TYPE);
			if(last_modified_type != null && last_modified_type != Tasks.LAST_MODIFIED_TYPE_LOCAL && last_modified_type != Tasks.LAST_MODIFIED_TYPE_SERVER) {
				values.remove(Lists.LAST_MODIFIED);
				values.remove(Lists.LAST_MODIFIED_TYPE);
			}

			SQLiteDatabase db = getWritableDatabase();
			return db.update(Lists.TABLE_NAME, values, selection, null);
		}
		
		/**
		 * Remove a list from the db
		 * @param id
		 * @return
		 */
		public int removeList (long id) {
			SQLiteDatabase db = getWritableDatabase();
			
			removeTasks(Tasks.LIST_ID +" = "+ id);		
			return db.delete(Lists.TABLE_NAME, Lists._ID +" = "+ id, null);
		}
		
		/**
		 * Remove a list from the db
		 * @param id
		 * @return
		 */
		public int removeLists (String selection) {
			SQLiteDatabase db = getWritableDatabase();
			
			Cursor c = db.query(Lists.TABLE_NAME, new String[] {Lists._ID}, selection, null, null, null, null);
			if(c.getCount() > 0) {
				//Remove tasks in these lists
				Long[] listIds = new Long[c.getCount()];
				for(int i=0; c.moveToNext(); i++) listIds[i] = c.getLong(c.getColumnIndex(Lists._ID));
				c.close();
				removeTasks(Tasks.LIST_ID +" IN ("+ TextUtils.join(",", listIds) +")");
					
				return db.delete(Lists.TABLE_NAME, Lists._ID +" IN ("+ TextUtils.join(", ", listIds) +")", null);
			} else return 0;
		}		
		
		/* ----------------------------------------------------------------
		 *                             Tasks
		 * ----------------------------------------------------------------*/
		
		/**
		 * Add a new task to the db.
		 * @param values
		 * @return
		 */
		public long addTask(ContentValues values) {
			SQLiteDatabase db = getWritableDatabase();
			Long parent, previous, listId;
			Integer completed, last_modified_type, order;
			Boolean flag;
			Cursor c;
			
			//Don't change id
			values.remove(Tasks._ID);
			
			//List id
			listId = values.getAsLong(Tasks.LIST_ID);
			if(!db.query(Lists.TABLE_NAME, null, Lists._ID +" = "+ listId, null, null, null, null).moveToFirst()) return -1;
			
			//Last modified
			if(values.getAsLong(Tasks.LAST_MODIFIED) == null) {
				values.put(Tasks.LAST_MODIFIED, System.currentTimeMillis());
			}
			
			//Last modified type
			last_modified_type = values.getAsInteger(Tasks.LAST_MODIFIED_TYPE);
			if(last_modified_type == null) {
				values.put(Tasks.LAST_MODIFIED_TYPE, Tasks.LAST_MODIFIED_TYPE_LOCAL);
			} else if(last_modified_type != Tasks.LAST_MODIFIED_TYPE_LOCAL && last_modified_type != Tasks.LAST_MODIFIED_TYPE_SERVER) return -1;

			//Completed
			completed = values.getAsInteger(Tasks.COMPLETED);
			if(completed != 0 && completed != 1) {
				if((flag = values.getAsBoolean(Tasks.COMPLETED)) != null) values.put(Tasks.COMPLETED, flag ? 1 : 0);
				else values.remove(Tasks.COMPLETED);
			}
			
			//Deleted
			values.put(Tasks.DELETED, 0);
			
			//Parent
			 //No parent => parent = 0
			if((parent = values.getAsLong(Tasks.PARENT)) == null) parent = 0L;
			 //check if parent exists TODO: exclude itself and it's childs
			else if(!db.query(Tasks.TABLE_NAME, null, Tasks._ID +" = "+ parent +" AND "+ Tasks.LIST_ID +" = "+ listId, null, null, null, null).moveToFirst()) return -1;
			values.put(Tasks.PARENT, parent);
			
			//Order
			 //No previous => put at the end of the parent's list
			if((previous = values.getAsLong(Tasks.PREVIOUS)) == null) {
				c = db.query(Tasks.TABLE_NAME, new String[] {"MAX("+ Tasks.ORDER +")"}, Tasks.LIST_ID +" = "+ listId +" AND "+ Tasks.PARENT +" = "+ parent, null, Tasks.LIST_ID, null, null);
				values.put(Tasks.ORDER, c.moveToFirst() ? c.getInt(0) + 1 : 0);
			} else {
				//check if previous exists and get order
				c = db.query(Tasks.TABLE_NAME, null, Tasks._ID +" = "+ previous +" AND "+ Tasks.LIST_ID +" = "+ listId, null, null, null, null);
				if(!c.moveToFirst()) return -1;
				order = c.getInt(c.getColumnIndex(Tasks.ORDER)) + 1;
				
				//make room in db
				ContentValues updateOrderValues = new ContentValues();
				updateOrderValues.put(Tasks.ORDER, Tasks.ORDER +" + 1");
				db.update(Tasks.TABLE_NAME, values, Tasks.LIST_ID +" = "+ listId +" AND "+ Tasks.PARENT +" = "+ parent +" AND "+ Tasks.ORDER +" >= "+ order, null);
				
				values.put(Tasks.ORDER, order);
			}
			
			return db.insert(Tasks.TABLE_NAME, null, values);
		}
		
		/**
		 * Update a task in the db
		 * @param values
		 * @return the number of rows affected, typically 1 or 0 if something went wrong
		 */
		public int updateTask (long id, ContentValues values) {	
			SQLiteDatabase db = getWritableDatabase();
			Long parent, previous, listId, oldListId, oldParent, oldPrevious;
			Integer completed, deleted, last_modified_type, order, oldOrder;
			Boolean flag;
			Cursor task, c;
			
			//Get existing task
			task = db.query(Tasks.TABLE_NAME, null, Tasks._ID +" = "+ id, null, null, null, null);
			if(!task.moveToFirst()) return 0;
			oldListId = task.getLong(task.getColumnIndex(Tasks.LIST_ID));
			oldParent = task.getLong(task.getColumnIndex(Tasks.PARENT));
			oldOrder = task.getInt(task.getColumnIndex(Tasks.ORDER));
			c = db.query(Tasks.TABLE_NAME, null, Tasks.LIST_ID +" = "+ oldListId +" AND "+ Tasks.PARENT +" = "+ oldParent +" AND "+ Tasks.ORDER +" < "+ oldOrder, null, null, null, Tasks.ORDER +" DESC");
			oldPrevious = c.moveToFirst() ? c.getLong(c.getColumnIndex(Tasks._ID)) : null;
			
			//Don't change id
			values.remove(Tasks._ID);
			
			//List id
			listId = values.getAsLong(Tasks.LIST_ID);
			if(listId == null) listId = task.getLong(task.getColumnIndex(Tasks.LIST_ID));
			else if(!db.query(Lists.TABLE_NAME, null, Lists._ID +" = "+ listId, null, null, null, null).moveToFirst()) return -1;

			//Completed
			completed = values.getAsInteger(Tasks.COMPLETED);
			if(completed != 0 && completed != 1) {
				if((flag = values.getAsBoolean(Tasks.COMPLETED)) != null) values.put(Tasks.COMPLETED, flag ? 1 : 0);
				else values.remove(Tasks.COMPLETED);
			}
			
			//Deleted
			deleted = values.getAsInteger(Tasks.DELETED);
			if(deleted != 0 && deleted != 1) {
				if((flag = values.getAsBoolean(Tasks.DELETED)) != null) values.put(Tasks.DELETED, flag ? 1 : 0);
				else values.remove(Tasks.DELETED);
			}
			
			//Last modified
			last_modified_type = values.getAsInteger(Tasks.LAST_MODIFIED_TYPE);
			if(last_modified_type != null && last_modified_type != Tasks.LAST_MODIFIED_TYPE_LOCAL && last_modified_type != Tasks.LAST_MODIFIED_TYPE_SERVER) {
				values.remove(Tasks.LAST_MODIFIED);
				values.remove(Tasks.LAST_MODIFIED_TYPE);
			}
			
			//Parent
			 //No parent => Old parent or 0 if new list_id
			if((parent = values.getAsLong(Tasks.PARENT)) == null) {
				if(listId != task.getLong(task.getColumnIndex(Tasks.LIST_ID))) parent = 0L;
				else parent = task.getLong(task.getColumnIndex(Tasks.PARENT));
			}
			 //check if parent exists TODO: exclude itself and it's childs
			else if(!db.query(Tasks.TABLE_NAME, null, Tasks._ID +" = "+ parent +" AND "+ Tasks.LIST_ID +" = "+ listId, null, null, null, null).moveToFirst()) return -1;
			values.put(Tasks.PARENT, parent);
			
			//Order
			previous = values.getAsLong(Tasks.PREVIOUS);			
			if(listId != oldListId || parent != oldParent || (previous != null && previous != oldPrevious)) {
				if(previous == null) {
					//No previous => put at the end of the parent's list
					c = db.query(Tasks.TABLE_NAME, new String[] {"MAX("+ Tasks.ORDER +")"}, Tasks.LIST_ID +" = "+ listId +" AND "+ Tasks.PARENT +" = "+ parent, null, Tasks.LIST_ID, null, null);
					order = c.moveToFirst() ? c.getInt(0) + 1 : 0;
				} else {
					//check if previous exists and get order
					c = db.query(Tasks.TABLE_NAME, null, Tasks._ID +" = "+ previous +" AND "+ Tasks.LIST_ID +" = "+ listId, null, null, null, null);
					if(!c.moveToFirst()) return -1;
					order = c.getInt(c.getColumnIndex(Tasks.ORDER)) + 1;
				}
				values.put(Tasks.ORDER, order);
				
				ContentValues updateOrderValues = new ContentValues();
				
				//Update source list
				updateOrderValues.put(Tasks.ORDER, Tasks.ORDER +" - 1");
				db.update(Tasks.TABLE_NAME, values, Tasks.LIST_ID +" = "+ oldListId +" AND "+ Tasks.PARENT +" = "+ oldParent +" AND "+ Tasks.ORDER +" > "+ oldOrder, null);
				
				//Update destination list
				updateOrderValues.put(Tasks.ORDER, Tasks.ORDER +" + 1");
				db.update(Tasks.TABLE_NAME, values, Tasks.LIST_ID +" = "+ listId +" AND "+ Tasks.PARENT +" = "+ parent +" AND "+ Tasks.ORDER +" > "+ order, null);
			} else values.remove(Tasks.ORDER);
			
			task.close();
			return db.update(Tasks.TABLE_NAME, values, Tasks._ID +" = "+ id, null);
		}
		
		/**
		 * Update multiple tasks in the db
		 * @param values
		 * @param selection
		 * @return
		 */
		public int updateTasks (ContentValues values, String selection) {
			Boolean flag;
			Integer deleted, completed, last_modified_type;			

			//Don't change ...
			values.remove(Tasks._ID);
			values.remove(Tasks.PARENT);
			values.remove(Tasks.ORDER);
			values.remove(Tasks.NAME);
			values.remove(Tasks.G_ID);
			values.remove(Tasks.LIST_ID);

			//Completed
			completed = values.getAsInteger(Tasks.COMPLETED);
			if(completed != 0 && completed != 1) {
				if((flag = values.getAsBoolean(Tasks.COMPLETED)) != null) values.put(Tasks.COMPLETED, flag ? 1 : 0);
				else values.remove(Tasks.COMPLETED);
			}
			
			//Deleted
			deleted = values.getAsInteger(Tasks.DELETED);
			if(deleted != 0 && deleted != 1) {
				if((flag = values.getAsBoolean(Tasks.DELETED)) != null) values.put(Tasks.DELETED, flag ? 1 : 0);
				else values.remove(Tasks.DELETED);
			}
			
			//Last modified
			last_modified_type = values.getAsInteger(Tasks.LAST_MODIFIED_TYPE);
			if(last_modified_type != null && last_modified_type != Tasks.LAST_MODIFIED_TYPE_LOCAL && last_modified_type != Tasks.LAST_MODIFIED_TYPE_SERVER) {
				values.remove(Tasks.LAST_MODIFIED);
				values.remove(Tasks.LAST_MODIFIED_TYPE);
			}

			SQLiteDatabase db = getWritableDatabase();
			return db.update(Tasks.TABLE_NAME, values, selection, null);
		}
		
		/**
		 * Remove a task from the db
		 * @param id
		 * @return
		 */
		public int removeTask (long id) {
			SQLiteDatabase db = getWritableDatabase();
			
			//Remove childs
			removeTasks(Tasks.PARENT +" = "+ id);
			
			return db.delete(Tasks.TABLE_NAME, Tasks._ID +" = "+ id, null);
		}
		
		/**
		 * Remove a task from the db
		 * @param id
		 * @return
		 */
		public int removeTasks (String selection) {
			SQLiteDatabase db = getWritableDatabase();			
			
			//Remove childs
			Cursor c = db.query(Tasks.TABLE_NAME, null, Tasks.PARENT +" IN(SELECT "+ Tasks._ID +" FROM "+ Tasks.TABLE_NAME +" WHERE "+ selection +")", null, null, null, null);
			if(c.getCount() > 0) {
				Long[] ids = new Long[c.getCount()];
				for(int i=0; c.moveToNext(); i++) ids[i] = c.getLong(c.getColumnIndex(Tasks._ID)); 
				c.close();
				removeTasks(Tasks._ID +" IN ("+ TextUtils.join(",", ids) +")");
			}
			
			return db.delete(Tasks.TABLE_NAME, selection, null);
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
