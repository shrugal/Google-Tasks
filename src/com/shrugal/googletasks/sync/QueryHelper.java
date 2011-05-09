package com.shrugal.googletasks.sync;

import java.util.ArrayList;
import java.util.Iterator;

import android.util.Log;

public class QueryHelper {
	
	/* Finals */
	private static final int CLIENT_VERSION = 0;
	
	/* Members */
	private String mUserId;
	private static Integer mActionId = 0;
	private ArrayList<String> mActions;
	private long mLastSync = 0;
	
	public QueryHelper () {
		mActions = new ArrayList<String> ();
	}
	
	public QueryHelper (long last_sync) {
		mActions = new ArrayList<String> ();
		mLastSync = last_sync;
	}
	
	public QueryHelper (long last_sync, String user_id) {
		mActions = new ArrayList<String> ();
		mLastSync = last_sync;
		mUserId = user_id;
	}
	
	/**
	 * Set the last sync point
	 * @param last_sync
	 */
	public void setLastSync (long last_sync) {
		mLastSync = last_sync;
	}
	
	/**
	 * Get the last sync point
	 * @return
	 */
	public long getLastSync () {
		return mLastSync;
	}
	
	/**
	 * Set the user id
	 * @param user_id
	 */
	public void setUserId (String user_id) {
		mUserId = user_id;
	}
	
	/**
	 * Get the user id
	 * @return
	 */
	public String getUserId () {
		return mUserId;
	}
	
	/**
	 * Add a get action to the query
	 * @param list_g_id
	 * @param deleted
	 * @param archived
	 * @return
	 */
	public QueryHelper addGetAction (Long list_g_id, Boolean deleted, Boolean archived) {
		int actionId;
		synchronized (mActionId) {
			actionId = mActionId++;
		}
		mActions.add("{"+
			"\"action_type\": \"get_all\","+
			"\"action_id\": "+ actionId +","+
			(list_g_id != null && mUserId != null ? "\"list_id\": \""+ mUserId +":"+ list_g_id +":0\"," : "")+
			"\"get_deleted\": "+ deleted.toString() +""+
			//"\"get_archived\": "+ archived.toString()+
		"}");
		
		return this;
	}
	
	/**
	 * Add a list creation action to the query
	 * @param name
	 * @return
	 */
	public int addCreateListAction (String name) {
		Log.w("Create list", name);
		if(mUserId == null) return -1;
		int actionId;
		synchronized (mActionId) {
			actionId = mActionId++;
		}
		
		mActions.add("{"+
			"\"action_type\": \"create\", "+
			"\"action_id\": "+ actionId +", "+
			"\"index\": 0, "+
			"\"entity_delta\":{"+
				"\"name\": \""+ name +"\", "+
				"\"creator_id\": "+ mUserId +", "+
				"\"entity_type\": \"GROUP\""+
			"}, "+
			"\"parent_id\": \""+ mUserId +":0:0\", "+
			"\"dest_parent_type\": \"GROUP\", "+
		"}");
		
		return actionId;
	}
	
	/**
	 * Add a task creation action to the query
	 * @param list_g_id
	 * @param name
	 * @param notes
	 * @param completed
	 * @return
	 */
	public int addCreateTaskAction (long list_g_id, String name, String notes, Boolean completed) {
		Log.w("Create task", name);
		if(mUserId == null) return -1;
		int actionId;
		synchronized (mActionId) {
			actionId = mActionId++;
		}
		
		mActions.add("{"+
			"\"action_type\": \"create\","+
			"\"action_id\": "+ actionId +","+
			"\"index\": 0,"+
			"\"entity_delta\":{"+
				"\"name\": \""+ name +"\","+
				"\"notes\": \""+ notes +"\","+
				"\"completed\": "+ completed.toString() +","+
				"\"creator_id\": null,"+
				"\"entity_type\": \"TASK\""+
			"},"+
			"\"parent_id\": \""+ mUserId +":"+ list_g_id +":0\","+
			"\"dest_parent_type\": \"GROUP\","+
			"\"list_id\": \""+ mUserId +":"+ list_g_id +":0\""+
		"}");
		
		return actionId;
	}
	
	/**
	 * Add a list update action to the query
	 * @param g_id
	 * @param name
	 * @param deleted
	 * @return
	 */
	public int addUpdateListAction (long g_id, String name, Boolean deleted) {
		Log.w("Update list", name);
		if(mUserId == null) return -1;
		int actionId;
		synchronized (mActionId) {
			actionId = mActionId++;
		}
		
		mActions.add("{"+
			"\"action_type\": \"update\","+
			"\"action_id\": "+ actionId +","+
			"\"id\": \""+ mUserId +":"+ g_id +":0,"+
			"\"entity_delta\": {"+
				"\"name\": \""+ name +"\","+
				"\"deleted\": \""+ deleted.toString() +"\""+
			"}"+
		"}");
		
		return actionId;
	}
	
	/**
	 * Add a task update action to the query
	 * @param g_id
	 * @param list_g_id
	 * @param name
	 * @param notes
	 * @param completed
	 * @param deleted
	 * @return
	 */
	public int addUpdateTaskAction (long g_id, long list_g_id, String name, String notes, Boolean completed, Boolean deleted) {
		Log.w("Update Task", name);
		if(mUserId == null) return -1;
		int actionId;
		synchronized (mActionId) {
			actionId = mActionId++;
		}
		
		mActions.add("{"+
			"\"action_type\": \"update\","+
			"\"action_id\": "+ actionId +","+
			"\"id\": \""+ mUserId +":"+ list_g_id +":"+ g_id +"\","+
			"\"entity_delta\": {"+
				"\"name\": \""+ name +"\","+
				"\"notes\": \""+ notes +"\","+
				"\"completed\": \""+ completed.toString() +"\","+
				"\"deleted\": \""+ deleted.toString() +"\""+
			"}"+
		"}");
		
		return actionId;
	}
	
	/**
	 * Add a task moving action to the query
	 * @param g_id
	 * @param old_list_g_id
	 * @param new_list_g_id
	 * @return
	 */
	public int addMoveTaskAction (long g_id, long old_list_g_id, long new_list_g_id) {
		Log.w("Move task", "From: "+ old_list_g_id +", To: "+ new_list_g_id);
		if(mUserId == null) return -1;
		int actionId;
		synchronized (mActionId) {
			actionId = mActionId++;
		}
		
		mActions.add("{"+
			"\"action_type\": \"move\","+
			"\"action_id\": "+ actionId +","+
			"\"id\":\""+ mUserId +":"+ old_list_g_id +":"+ g_id +"\","+
			"\"source_list\":\""+ mUserId +":"+ old_list_g_id +":0\","+
			"\"dest_parent\":\""+ mUserId +":"+ new_list_g_id +":0\","+
			"\"dest_list\":\""+ mUserId +":"+ new_list_g_id +":0\""+
		"}");
		
		return actionId;
	}
	
	/**
	 * Get the query as a JSON formated string
	 */
	public String toString () {
		//Start
		StringBuilder builder = new StringBuilder("{\"action_list\":[");
		
		//Actions
		Iterator<String> iter = mActions.iterator();
		while(iter.hasNext()) {
			builder.append(iter.next());
			if(iter.hasNext()) builder.append(",");
		}
		builder.append("]");
		
		//Meta info
		builder.append(", \"client_version\": "+ CLIENT_VERSION);
		builder.append(", \"latest_sync_point\": "+ mLastSync);

		//End
		builder.append("}");
		Log.i("Google Tasks", builder.toString());
		return builder.toString();
	}
	
	public String toString (int start, int length) {
		if(length <= 0 || size()-1 < start) return null;
		int end = Math.min(size(), start+length);
		//Start
		StringBuilder builder = new StringBuilder("{\"action_list\":[");
		
		//Actions
		for(int i=start; i<end ; i++) {
			builder.append(mActions.get(i));
			if(i+1<end) builder.append(",");
		}
		builder.append("]");
		
		//Meta info
		builder.append(", \"client_version\": "+ CLIENT_VERSION);
		builder.append(", \"latest_sync_point\": "+ mLastSync);

		//End
		builder.append("}");
		Log.i("Google Tasks", builder.toString());
		return builder.toString();
	}
	
	public static String getUserId (String g_id) {
		return g_id.split(":")[0];
	}
	
	public static long getListId (String g_id) {
		return Long.valueOf(g_id.split(":")[1]);
	}
	
	public static long getTaskId (String g_id) {
		return Long.valueOf(g_id.split(":")[2]);
	}
	
	public int size () {
		return mActions.size();
	}
}
