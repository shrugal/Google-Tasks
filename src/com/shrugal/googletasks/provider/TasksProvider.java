package com.shrugal.googletasks.provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import android.util.Log;

public class TasksProvider extends ContentProvider {
	
	//Uri stuff
    public static final String AUTHORITY = "com.shrugal.googletasks.provider";
    private static UriMatcher sUriMatcher;
	private static final int LISTS = 0;
	private static final int LIST_ID = 1;
	private static final int TASKS = 2;
	private static final int TASK_ID = 3;
	
	//Other finals
	private static final String[] STRUCTURE_PROJECTION = new String[] {Tasks._ID, Tasks.LIST_ID, Tasks.PARENT, Tasks.LEFT, Tasks.RIGHT};
	private static final String ALIAS_REGEX = "("+ TextUtils.join("|", Tasks.KEYS) +")";
	private static final String TAG = "Google Tasks";
	
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
		int rankIndex = -1, childsIndex = -1;
		String groupBy = null;
		
		if(projection != null) {
			List<String> projectionList =  Arrays.asList(projection);
			rankIndex = projectionList.indexOf(Tasks.RANK);
			childsIndex = projectionList.indexOf(Tasks.CHILDS);
		}
		
		//Table, selection and order
		switch(sUriMatcher.match(uri)) {
			case LIST_ID:
				builder.appendWhere(Lists._ID +" = "+ uri.getPathSegments().get(1));
			case LISTS:
				builder.setTables(Lists.TABLE_NAME);
				order = TextUtils.isEmpty(order) ? Lists.DEFAULT_ORDER : order +", "+ Lists.DEFAULT_ORDER;
				break;
			case TASK_ID:
				builder.appendWhere((rankIndex >= 0 || childsIndex >= 0 ? "a." : "")+ Tasks._ID +" = "+ uri.getPathSegments().get(1));
			case TASKS:
				if(rankIndex >= 0 || childsIndex >= 0) {
					//Append aliases
					projection = appendAliasInArray("a", true, projection);
					selection = appendAlias("a", false, selection);
					order = TextUtils.isEmpty(order) ? appendAlias("a", false, Tasks.DEFAULT_ORDER) : appendAlias("a", false, order +", "+ Tasks.DEFAULT_ORDER);
					groupBy = "a."+ Tasks._ID;
					
					//Create joins and alter projection
					String tables = Tasks.TABLE_NAME +" AS a";
					if(rankIndex >= 0) {
						tables +=  " LEFT JOIN "+ Tasks.TABLE_NAME +" AS b ON (a."+ Tasks.LIST_ID +" = b."+ Tasks.LIST_ID +" AND a."+ Tasks.LEFT +" BETWEEN b."+ Tasks.LEFT +" AND b."+ Tasks.RIGHT +")";
						projection[rankIndex] = "COUNT(DISTINCT b."+ Tasks._ID +")-1 AS "+ Tasks.RANK;
					}
					if(childsIndex >= 0) {
						tables +=  " LEFT JOIN "+ Tasks.TABLE_NAME +" AS c ON (a."+ Tasks.LIST_ID +" = c."+ Tasks.LIST_ID +" AND c."+ Tasks.LEFT +" BETWEEN a."+ Tasks.LEFT +" AND a."+ Tasks.RIGHT +")";
						projection[childsIndex] = "COUNT(DISTINCT c."+ Tasks._ID +")-1 AS "+ Tasks.CHILDS;
					}
					builder.setTables(tables);
				} else {
					builder.setTables(Tasks.TABLE_NAME);
					order = TextUtils.isEmpty(order) ? Tasks.DEFAULT_ORDER : order +", "+ Tasks.DEFAULT_ORDER;
				}
				break;
			default:
				throw new IllegalArgumentException("Unknown URI "+ uri);
		}
		
		//Create cursor
		Cursor c = builder.query(mDb.getReadableDatabase(), projection, selection, selectionArgs, groupBy, null, order);
		c.setNotificationUri(getContext().getContentResolver(), uri);

		//DEBUG
		boolean debug = false;
		if(debug) {
			if(c.moveToFirst()) {
				c.moveToPrevious();
				String[] columns = c.getColumnNames();
				int n = columns.length;
				
				final int BUFFER_LENGTH = 100;
				final int SPACER = 2;
				final int COLUMN_WIDTH = BUFFER_LENGTH / n;
				
				StringBuffer buffer = new StringBuffer();
				String s;
				
				for(int i=0; i<n; i++) {
					s = columns[i];
					if(s.length() > COLUMN_WIDTH - SPACER)  s = s.substring(0, COLUMN_WIDTH - SPACER);
					while(s.length() < COLUMN_WIDTH) s += " ";
					buffer.append(s);
				}
				Log.i(TAG, buffer.toString());
				buffer = new StringBuffer();
				for(int i=0; i<BUFFER_LENGTH; i++) buffer.append("-");
				Log.i(TAG, buffer.toString());
				
				while(c.moveToNext()) {
					buffer = new StringBuffer();
					for(int i=0; i<n; i++) {
						s = c.getString(i);
						if(s == null) s = "";
						if(s.length() > COLUMN_WIDTH - SPACER)  s = s.substring(0, COLUMN_WIDTH - SPACER);
						while(s.length() < COLUMN_WIDTH) s += " ";
						buffer.append(s);
					}
					Log.i(TAG, buffer.toString());
				}
				c.moveToFirst();
				c.moveToPrevious();
			}
		}
		
		return c;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
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
	
	private String[] appendAliasInArray (String alias, boolean appendAs, String[] a) {
		if(alias == null || a == null) return a;
		for(int i=0; i<a.length; i++) a[i] = appendAlias(alias, appendAs, a[i]);
		return a;
	}
	
	private String appendAlias (String alias, boolean appendAs, String s) {
		if(alias == null || s == null) return s;
		//TODO: Truly interprete the where statement
		s = s.replaceAll(ALIAS_REGEX, alias +".$1"+ (appendAs ? " AS $1" : ""));
		return s;
	}
	
		
	/* ------------------------------------------------------------
	 *                      Database Helper
	 * ------------------------------------------------------------ */
	private class DatabaseHelper extends SQLiteOpenHelper {

		/* Finals */
		private static final int VERSION = 1;
		private static final String DB_NAME = "googletasks";
		
		//Create Querys
		private static final String LISTS_CREATE_QUERY = "CREATE TABLE "+ Lists.TABLE_NAME +" ("+
			Lists._ID +" INTEGER PRIMARY KEY AUTOINCREMENT, "+
			Lists.G_ID +" INTEGER, "+
			Lists.NAME +" TEXT, "+
			Lists.LAST_MODIFIED +" INTEGER, "+
			Lists.LAST_MODIFIED_TYPE +" INTEGER, "+
			Lists.DELETED +" INTEGER, "+
			Lists.ORDER +" INTEGER"+
		")";
		private static final String TASKS_CREATE_QUERY = "CREATE TABLE "+ Tasks.TABLE_NAME +" ("+
			Tasks._ID +" INTEGER PRIMARY KEY AUTOINCREMENT, "+
			Tasks.G_ID +" INTEGER, "+
			Tasks.LIST_ID +" INTEGER, "+
			Tasks.PARENT +" INTEGER, "+
			Tasks.LEFT +" INTEGER, "+
			Tasks.RIGHT +" INTEGER, "+
			Tasks.NAME +" TEXT, "+
			Tasks.NOTES +" TEXT, "+
			Tasks.DATE +" INTEGER, "+
			Tasks.LAST_MODIFIED +" INTEGER, "+
			Tasks.LAST_MODIFIED_TYPE +" INTEGER, "+
			Tasks.COMPLETED +" INTEGER, "+
			Tasks.DELETED +" INTEGER"+
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
			flag = values.getAsBoolean(Lists.DELETED);
			if(flag != null) {
				deleted = flag ? 1 : 0;
			} else {
				deleted = values.getAsInteger(Lists.DELETED);
				if(deleted != null && deleted != 0 && deleted != 1) throw new IllegalArgumentException("Illegal value for deleted "+ deleted);
			}
			if(deleted != null) values.put(Lists.DELETED, deleted);
			else values.remove(Lists.DELETED);
			
			//Last modified
			last_modified_type = values.getAsInteger(Lists.LAST_MODIFIED_TYPE);
			if(last_modified_type != null && last_modified_type != Lists.LAST_MODIFIED_TYPE_LOCAL && last_modified_type != Tasks.LAST_MODIFIED_TYPE_SERVER) {
				values.remove(Lists.LAST_MODIFIED);
				values.remove(Lists.LAST_MODIFIED_TYPE);
			}

			SQLiteDatabase db = getWritableDatabase();
			//TODO: Update child tasks if deleted changed => also last modified
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
			values.remove(Lists.G_ID);
			values.remove(Lists.ORDER);
			values.remove(Lists.NAME);

			//Deleted
			flag = values.getAsBoolean(Lists.DELETED);
			if(flag != null) {
				deleted = flag ? 1 : 0;
			} else {
				deleted = values.getAsInteger(Lists.DELETED);
				if(deleted != null && deleted != 0 && deleted != 1) throw new IllegalArgumentException("Illegal value for deleted "+ deleted);
			}
			if(deleted != null) values.put(Lists.DELETED, deleted);
			else values.remove(Lists.DELETED);
			
			//Last modified
			last_modified_type = values.getAsInteger(Lists.LAST_MODIFIED_TYPE);
			if(last_modified_type != null && last_modified_type != Tasks.LAST_MODIFIED_TYPE_LOCAL && last_modified_type != Tasks.LAST_MODIFIED_TYPE_SERVER) {
				values.remove(Lists.LAST_MODIFIED);
				values.remove(Lists.LAST_MODIFIED_TYPE);
			}

			SQLiteDatabase db = getWritableDatabase();
			//TODO: Update child tasks if deleted changed => also last modified
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
		 * Gets all childs's ids (including childs of childs) of the given task
		 * @param id
		 * @param include_self
		 * @return array of childs's ids
		 */
		private ArrayList<Long> getChildTasks (long id, boolean include_self) {
			return getChildTasks(Tasks._ID +" = "+ id, include_self);
		}
		
		/**
		 * Gets all childs's ids (including childs of childs) of the tasks given by selection
		 * @param selection
		 * @param include_self
		 * @return array of childs's ids
		 */
		private ArrayList<Long> getChildTasks (String selection, boolean include_self) {
			ArrayList<Long> childs = new ArrayList<Long>();
			SQLiteDatabase db = getReadableDatabase();
			SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
			
			selection = appendAlias("a", false, selection);
			String between = include_self ? "a."+ Tasks.LEFT +" AND a."+ Tasks.RIGHT : "a."+ Tasks.LEFT +"+1 AND a."+ Tasks.RIGHT +"-1";
			builder.setTables(Tasks.TABLE_NAME +" as a LEFT JOIN "+ Tasks.TABLE_NAME +" as b ON a."+ Tasks.LIST_ID +" = b."+ Tasks.LIST_ID +" AND b."+ Tasks.LEFT +" BETWEEN "+ between);
			builder.setDistinct(true);
			Cursor c = builder.query(db, new String[] {"b."+ Tasks._ID}, selection, null, "b."+ Tasks._ID, null, "b."+ Tasks._ID);
			
			while(c.moveToNext())  childs.add(c.getLong(0));
			c.close();
			
			return childs;
		}
		
		/**
		 * Add a new task to the db.
		 * @param values
		 * @return
		 */
		public long addTask(ContentValues values) throws IllegalArgumentException {
			SQLiteDatabase db = getWritableDatabase();
			Long parent, previous, listId;
			Integer completed, last_modified_type, left;
			Boolean flag;
			Cursor c;
			
			//Don't set ...
			values.remove(Tasks._ID);
			values.remove(Tasks.LEFT);
			values.remove(Tasks.RIGHT);

			/* ----------- List ID ----------- */
			listId = values.getAsLong(Tasks.LIST_ID);
			if(!db.query(Lists.TABLE_NAME, null, Lists._ID +" = "+ listId, null, null, null, null).moveToFirst()) {
				throw new IllegalArgumentException("Unknown list "+ listId);
			}

			/* ----------- Last modified ----------- */
			if(values.getAsLong(Tasks.LAST_MODIFIED) == null) {
				values.put(Tasks.LAST_MODIFIED, System.currentTimeMillis());
			}

			/* ----------- Last modified type ----------- */
			last_modified_type = values.getAsInteger(Tasks.LAST_MODIFIED_TYPE);
			if(last_modified_type == null) {
				values.put(Tasks.LAST_MODIFIED_TYPE, Tasks.LAST_MODIFIED_TYPE_LOCAL);
			} else if(last_modified_type != Tasks.LAST_MODIFIED_TYPE_LOCAL && last_modified_type != Tasks.LAST_MODIFIED_TYPE_SERVER) {
				throw new IllegalArgumentException("Unknown last modified type "+ last_modified_type);
			}

			/* ----------- Completed ----------- */
			flag = values.getAsBoolean(Tasks.COMPLETED);
			if(flag != null) {
				completed = flag ? 1 : 0;
			} else {
				completed = values.getAsInteger(Tasks.COMPLETED);
				if (completed != null && completed != 0 && completed != 1) throw new IllegalArgumentException("Illegal value for completed "+ completed);
				else if(completed == null) completed = 0;
			}
			values.put(Tasks.COMPLETED, completed);

			/* ----------- Deleted ----------- */
			values.put(Tasks.DELETED, 0);

			/* ----------- Parent ----------- */
			 //No parent => parent = 0
			if((parent = values.getAsLong(Tasks.PARENT)) == null || parent == 0) parent = 0L;
			 //check if parent exists
			else if(!db.query(Tasks.TABLE_NAME, STRUCTURE_PROJECTION, Tasks._ID +" = "+ parent +" AND "+ Tasks.LIST_ID +" = "+ listId, null, null, null, null).moveToFirst()) {
				throw new IllegalArgumentException("Unknown parent "+ parent +" in list "+ listId);
			}
			values.put(Tasks.PARENT, parent);

			/* ----------- Left, Right ----------- */
			if((previous = values.getAsLong(Tasks.PREVIOUS)) == null) {
				//No previous => put at the end of the parent's list
				if(parent != 0) {
					//Parent exists => take info from parent
					c = db.query(Tasks.TABLE_NAME, STRUCTURE_PROJECTION, Tasks._ID +" = "+ parent, null, null, null, null);
					if(!c.moveToFirst()) throw new IllegalArgumentException("Unknown parent "+ parent +" in list "+ listId);
					left = c.getInt(c.getColumnIndex(Tasks.RIGHT));
				} else {
					//Parent does not exist => take info from neighbors or use start value
					c = db.query(Tasks.TABLE_NAME, new String[] {"MAX("+ Tasks.RIGHT +")"}, Tasks.LIST_ID +" = "+ listId +" AND "+ Tasks.PARENT +" = "+ parent, null, Tasks.LIST_ID, null, null);
					left = c.moveToFirst() ? c.getInt(0) + 1 : 0;
				}
			} else {
				if(parent == previous) {
					//Put at the top of the list
					if(parent != 0) {
						//Parent exists => take info from parent
						c = db.query(Tasks.TABLE_NAME, STRUCTURE_PROJECTION, Tasks._ID +" = "+ parent, null, null, null, null);
						if(!c.moveToFirst()) throw new IllegalArgumentException("Unknown parent "+ parent +" in list "+ listId);
						left = c.getInt(c.getColumnIndex(Tasks.LEFT)) + 1;
					} else {
						//Parent does not exist => take info from neighbors or use start value
						c = db.query(Tasks.TABLE_NAME, new String[] {"MIN("+ Tasks.LEFT +")"}, Tasks.LIST_ID +" = "+ listId +" AND "+ Tasks.PARENT +" = "+ parent, null, Tasks.LIST_ID, null, null);
						left = c.moveToFirst() ? c.getInt(0) : 0;
					}
				} else {
					//Check if previous exists and set left
					c = db.query(Tasks.TABLE_NAME, STRUCTURE_PROJECTION, Tasks._ID +" = "+ previous +" AND "+ Tasks.PARENT +" = "+ parent +" AND "+ Tasks.LIST_ID +" = "+ listId, null, null, null, null);
					if(!c.moveToFirst()) throw new IllegalArgumentException("Unknown previous "+ previous +" under "+ parent +" in list "+ listId);
					left = c.getInt(c.getColumnIndex(Tasks.RIGHT)) + 1;
				}
			}
			values.put(Tasks.LEFT, left);
			values.put(Tasks.RIGHT, left+1);
			ContentValues updateValues = new ContentValues();

			/* ----------- Make room in DB ----------- */
			//TODO: Figure out a way to to this with ContentValues
			db.execSQL("UPDATE "+ Tasks.TABLE_NAME +" SET "+ Tasks.LEFT +" = "+ Tasks.LEFT +" + 2 WHERE "+ Tasks.LEFT +" >= "+ left +" AND "+ Tasks.LIST_ID +" = "+ listId);			
			db.execSQL("UPDATE "+ Tasks.TABLE_NAME +" SET "+ Tasks.RIGHT +" = "+ Tasks.RIGHT +" + 2 WHERE "+ Tasks.RIGHT +" >= "+ left +" AND "+ Tasks.LIST_ID +" = "+ listId);
			
			/* ----------- Update completed in childs ----------- */
			if(completed != null && completed == 0) {
				updateValues.clear();
				updateValues.put(Tasks.COMPLETED, completed);
				updateValues.put(Tasks.LAST_MODIFIED, values.getAsInteger(Tasks.LAST_MODIFIED));
				updateValues.put(Tasks.LAST_MODIFIED_TYPE, values.getAsInteger(Tasks.LAST_MODIFIED_TYPE));
				
				//Update parents
				db.update(Tasks.TABLE_NAME, updateValues, Tasks.LEFT +" < "+ left +" AND "+ Tasks.RIGHT +" > "+ (left+1), null);
			}
			
			return db.insert(Tasks.TABLE_NAME, null, values);
		}
		
		/**
		 * Update a task in the db
		 * @param values
		 * @return the number of rows affected, typically 1 or 0 if something went wrong
		 */
		public int updateTask (long id, ContentValues values) throws IllegalArgumentException {	
			SQLiteDatabase db = getWritableDatabase();
			Long parent, previous, follower, listId, oldListId, oldParent, oldPrevious;
			Integer completed, deleted, last_modified_type, left, right, oldLeft, oldRight, oldCompleted, oldDeleted;
			Boolean flag;
			Cursor c;
			ContentValues updateValues = new ContentValues();;
			
			//Don't change ...
			values.remove(Tasks._ID);
			values.remove(Tasks.LEFT);
			values.remove(Tasks.RIGHT);

			/* ----------- Existing task ----------- */
			c = db.query(Tasks.TABLE_NAME, null, Tasks._ID +" = "+ id, null, null, null, null);
			if(!c.moveToFirst()) return 0;
			 //list id
			oldListId = c.getLong(c.getColumnIndex(Tasks.LIST_ID));
			 //parent
			oldParent = c.getLong(c.getColumnIndex(Tasks.PARENT));
			 //left, right
			oldLeft = c.getInt(c.getColumnIndex(Tasks.LEFT));
			oldRight = c.getInt(c.getColumnIndex(Tasks.RIGHT));
			 //completed
			oldCompleted = c.getInt(c.getColumnIndex(Tasks.COMPLETED));
			 //deleted
			oldDeleted = c.getInt(c.getColumnIndex(Tasks.DELETED));
			 //previous
			c = db.query(Tasks.TABLE_NAME, null, Tasks.LIST_ID +" = "+ oldListId +" AND "+ Tasks.RIGHT +" = "+ (oldLeft-1), null, null, null, null);
			oldPrevious = c.moveToFirst() ? c.getLong(c.getColumnIndex(Tasks._ID)) : 0L;
			c.close();

			/* ----------- Completed ----------- */
			flag = values.getAsBoolean(Tasks.COMPLETED);
			if(flag != null) {
				completed = flag ? 1 : 0;
			} else {
				completed = values.getAsInteger(Tasks.COMPLETED);
				if (completed != null && completed != 0 && completed != 1) throw new IllegalArgumentException("Illegal value for completed "+ completed);
			}
			if(completed != null) values.put(Tasks.COMPLETED, completed);
			else values.remove(Tasks.COMPLETED);

			/* ----------- Deleted ----------- */
			flag = values.getAsBoolean(Tasks.DELETED);
			if(flag != null) {
				deleted = flag ? 1 : 0;
			} else {
				deleted = values.getAsInteger(Tasks.DELETED);
				if(deleted != null && deleted != 0 && deleted != 1) throw new IllegalArgumentException("Illegal value for deleted "+ deleted);
			}
			if(deleted != null) values.put(Tasks.DELETED, deleted);
			else values.remove(Tasks.DELETED);

			/* ----------- Last modified ----------- */
			last_modified_type = values.getAsInteger(Tasks.LAST_MODIFIED_TYPE);
			if(last_modified_type != null && last_modified_type != Tasks.LAST_MODIFIED_TYPE_LOCAL && last_modified_type != Tasks.LAST_MODIFIED_TYPE_SERVER) {
				values.remove(Tasks.LAST_MODIFIED);
				values.remove(Tasks.LAST_MODIFIED_TYPE);
			}

			/* ----------- List ID ----------- */
			listId = values.getAsLong(Tasks.LIST_ID);
			if(listId == null) listId = oldListId;
			else if(!db.query(Lists.TABLE_NAME, null, Lists._ID +" = "+ listId, null, null, null, null).moveToFirst()) {
				throw new IllegalArgumentException("Unknown list "+ listId);
			}

			/* ----------- Parent ----------- */
			//No parent => Old parent or 0 if new list_id
			if((parent = values.getAsLong(Tasks.PARENT)) == null) {
				if(listId != oldListId) parent = 0L;
				else parent = oldParent;
				values.put(Tasks.PARENT, parent);
			}
			//check if parent is 0
			else if(parent == 0) {
				//nothing to do
			}
			//check if parent is itself or one of its childs
			else if(getChildTasks(id, true).contains(parent)) {
				throw new IllegalArgumentException("Illegal parent "+ parent +" is the same as "+ id +" or one of its childs");
			}
			//check if parent exists
			else if(!db.query(Tasks.TABLE_NAME, STRUCTURE_PROJECTION, Tasks._ID +" = "+ parent +" AND "+ Tasks.LIST_ID +" = "+ listId, null, null, null, null).moveToFirst()) {
				throw new IllegalArgumentException("Unknown parent "+ parent +" in list "+ listId);
			}			

			/* ----------- Follower ----------- */
			if(values.containsKey(Tasks.FOLLOWER)) {
				follower = values.getAsLong(Tasks.FOLLOWER);
				if(follower != null) {
					//Try to get the previous
					c = db.query(Tasks.TABLE_NAME +" AS a INNER JOIN "+ Tasks.TABLE_NAME +" AS b ON a."+ Tasks.LIST_ID +" = b."+ Tasks.LIST_ID +" AND (a."+ Tasks.RIGHT +" = b."+ Tasks.LEFT +" - 1 OR a."+ Tasks.LEFT +" = b."+ Tasks.LEFT +" - 1)", new String[] {"a."+ Tasks._ID}, "b."+ Tasks._ID +" = "+ follower, null, null, null, null);
					if(c.moveToFirst()) values.put(Tasks.PREVIOUS, c.getLong(0));
					else values.put(Tasks.PREVIOUS, 0L);
				}
			}
			values.remove(Tasks.FOLLOWER);

			/* ----------- Previous ----------- */
			//No previous => end of the list or null if nothing else changed
			if((previous = values.getAsLong(Tasks.PREVIOUS)) == null) {
				if(listId != oldListId || parent != oldParent) {
					c = db.query(Tasks.TABLE_NAME, STRUCTURE_PROJECTION, Tasks.LIST_ID +" = "+ listId +" AND "+ Tasks.PARENT +" = "+ parent, null, null, null, Tasks.RIGHT +" DESC");
					previous = c.moveToFirst() ? c.getLong(c.getColumnIndex(Tasks._ID)) : parent;
				} else {
					previous = null;
				}
			}
			//check if previous has changed
			else if(previous == oldPrevious && parent == oldParent && listId == oldListId) {
				previous = null;
			}
			//check if previous is 0 or = parent
			else if(previous == 0 && parent == 0) {
				//nothing to do
			}
			//check if previous is itself or one of its childs
			else if(getChildTasks(id, true).contains(previous)) {
				throw new IllegalArgumentException("Illegal previous "+ previous +" is the same as "+ id +" or one of its childs");
			}
			//check if previous = parent
			else if(previous == parent) {
				//nothing to do
			}
			//check if previous exists
			else if(!db.query(Tasks.TABLE_NAME, STRUCTURE_PROJECTION, Tasks._ID +" = "+ previous +" AND "+ Tasks.LIST_ID +" = "+ listId +" AND "+ Tasks.PARENT +" = "+ parent, null, null, null, null).moveToFirst()) {
				throw new IllegalArgumentException("Unknown previous "+ previous +" with parent "+ parent +" in list "+ listId);
			}
			values.remove(Tasks.PREVIOUS);

			/* ----------- Move ----------- */
			//Something changed?
			if(previous != null) {
				int space = (oldRight+1) - oldLeft;
				int leftDest, leftSource, rightSource, diff;
				/* - Parameters for making space - */
				if(previous == 0) {
					leftDest = 0;
				} else {
					c = db.query(Tasks.TABLE_NAME, STRUCTURE_PROJECTION, Tasks._ID +" = "+ previous, null, null, null, null);
					if(!c.moveToFirst()) throw new IllegalArgumentException("Unknown previous "+ previous +" with parent "+ parent +" in list "+ listId);
					if(previous == parent) {
						//Between previous
						leftDest = c.getInt(c.getColumnIndex(Tasks.LEFT))+1;
					} else {
						//Behind previous
						leftDest = c.getInt(c.getColumnIndex(Tasks.RIGHT))+1;
					}
				}
				
				/* - Parameters after making space - */
				if(listId != oldListId || oldLeft < leftDest) {
					//nothing changed?
					leftSource = oldLeft;
				} else {
					//left and right changed
					leftSource = oldLeft + space;
				}
				rightSource = leftSource + space - 1;
				diff = leftDest - leftSource;
				
				/* - Parameters after all operations - */
				if(listId != oldListId || oldLeft > leftDest) left = leftDest;
				else left = leftDest - space;
				right = left + space;
				
				/* ----------- DB operations ----------- */
				//TODO: Figure out a way to do this with ContentValues
				
				//Make room
				db.execSQL("UPDATE "+ Tasks.TABLE_NAME +" SET "+ Tasks.LEFT +" = "+ Tasks.LEFT +" + "+ space +" WHERE "+ Tasks.LIST_ID +" = "+ listId +" AND "+ Tasks.LEFT +" >= "+ leftDest);
				db.execSQL("UPDATE "+ Tasks.TABLE_NAME +" SET "+ Tasks.RIGHT +" = "+ Tasks.RIGHT +" + "+ space +" WHERE "+ Tasks.LIST_ID +" = "+ listId +" AND "+ Tasks.RIGHT +" >= "+ leftDest);
				
				//Move
				db.execSQL("UPDATE "+ Tasks.TABLE_NAME +" SET "+ Tasks.LEFT +" = "+ Tasks.LEFT +" + "+ diff +", "+ Tasks.RIGHT +" = "+ Tasks.RIGHT +" + "+ diff +", "+ Tasks.LIST_ID +" = "+ listId +" WHERE "+ Tasks.LIST_ID +" = "+ oldListId +" AND "+ Tasks.LEFT +" BETWEEN "+ leftSource +" AND "+ rightSource);
				
				//Cleanup
				db.execSQL("UPDATE "+ Tasks.TABLE_NAME +" SET "+ Tasks.LEFT +" = "+ Tasks.LEFT +" - "+ space +" WHERE "+ Tasks.LIST_ID +" = "+ oldListId +" AND "+ Tasks.LEFT +" >= "+ leftSource);
				db.execSQL("UPDATE "+ Tasks.TABLE_NAME +" SET "+ Tasks.RIGHT +" = "+ Tasks.RIGHT +" - "+ space +" WHERE "+ Tasks.LIST_ID +" = "+ oldListId +" AND "+ Tasks.RIGHT +" >= "+ leftSource);
				
				//Set new parent
				updateValues.clear();
				updateValues.put(Tasks.PARENT, parent);
				db.update(Tasks.TABLE_NAME, updateValues, Tasks._ID +" = "+ id, null);
			} else {
				left = oldLeft;
				right = oldRight;
			}

			/* ----------- Update completed in parents or childs ----------- */
			if(completed != null || (previous != null && oldCompleted == 0)) {
				Integer tempCompleted = completed != null ? completed : oldCompleted;
				updateValues.clear();
				updateValues.put(Tasks.COMPLETED, tempCompleted);
				updateValues.put(Tasks.LAST_MODIFIED, values.getAsInteger(Tasks.LAST_MODIFIED));
				updateValues.put(Tasks.LAST_MODIFIED_TYPE, values.getAsInteger(Tasks.LAST_MODIFIED_TYPE));
				
				if(tempCompleted == 1)
					//Update childs
					db.update(Tasks.TABLE_NAME, updateValues, Tasks.LEFT +" BETWEEN "+ left +" AND "+ right, null);
				else
					//Update parents
					db.update(Tasks.TABLE_NAME, updateValues, Tasks.LEFT +" < "+ left +" AND "+ Tasks.RIGHT +" > "+ right, null);
			}

			/* ----------- Update deleted in parents or childs ----------- */
			if(deleted != null || (previous != null && oldDeleted == 0)) {
				Integer tempDeleted = deleted != null ? deleted : oldDeleted;
				//Update childs
				updateValues.clear();
				updateValues.put(Tasks.DELETED, tempDeleted);
				updateValues.put(Tasks.LAST_MODIFIED, values.getAsInteger(Tasks.LAST_MODIFIED));
				updateValues.put(Tasks.LAST_MODIFIED_TYPE, values.getAsInteger(Tasks.LAST_MODIFIED_TYPE));
				
				if(tempDeleted == 1)
					//Update childs
					db.update(Tasks.TABLE_NAME, updateValues, Tasks.LEFT +" BETWEEN "+ left +" AND "+ right, null);
				else
					//Update parents
					db.update(Tasks.TABLE_NAME, updateValues, Tasks.LEFT +" < "+ left +" AND "+ Tasks.RIGHT +" > "+ right, null);
			}
			
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
			values.remove(Tasks.G_ID);
			values.remove(Tasks.LIST_ID);
			values.remove(Tasks.PARENT);
			values.remove(Tasks.LEFT);
			values.remove(Tasks.RIGHT);
			values.remove(Tasks.NAME);

			/* ----------- Completed ----------- */
			flag = values.getAsBoolean(Tasks.COMPLETED);
			if(flag != null) {
				completed = flag ? 1 : 0;
			} else {
				completed = values.getAsInteger(Tasks.COMPLETED);
				if (completed != null && completed != 0 && completed != 1) throw new IllegalArgumentException("Illegal value for completed "+ completed);
			}
			if(completed != null) values.put(Tasks.COMPLETED, completed);
			else values.remove(Tasks.COMPLETED);

			/* ----------- Deleted ----------- */
			flag = values.getAsBoolean(Tasks.DELETED);
			if(flag != null) {
				deleted = flag ? 1 : 0;
			} else {
				deleted = values.getAsInteger(Tasks.DELETED);
				if(deleted != null && deleted != 0 && deleted != 1) throw new IllegalArgumentException("Illegal value for deleted "+ deleted);
			}
			if(deleted != null) values.put(Tasks.DELETED, deleted);
			else values.remove(Tasks.DELETED);

			/* ----------- Last modified ----------- */
			last_modified_type = values.getAsInteger(Tasks.LAST_MODIFIED_TYPE);
			if(last_modified_type != null && last_modified_type != Tasks.LAST_MODIFIED_TYPE_LOCAL && last_modified_type != Tasks.LAST_MODIFIED_TYPE_SERVER) {
				values.remove(Tasks.LAST_MODIFIED);
				values.remove(Tasks.LAST_MODIFIED_TYPE);
			}
			
			//TODO: Update completed and deleted fields of parents and childs => also last modified

			SQLiteDatabase db = getWritableDatabase();
			return db.update(Tasks.TABLE_NAME, values, selection, null);
		}
		
		/**
		 * Remove a task and its childs from the db
		 * @param id
		 * @return
		 */
		public int removeTask (long id) {
			return removeTasks(Tasks._ID +" = "+ id);
		}
		
		/**
		 * Remove all tasks given by selection and their childs from the db
		 * @param id
		 * @return
		 */
		public int removeTasks (String selection) {
			SQLiteDatabase db = getWritableDatabase();	
			
			return db.delete(Tasks.TABLE_NAME, Tasks._ID +" IN ("+ TextUtils.join(", ", getChildTasks(selection, true)) +")", null);
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
