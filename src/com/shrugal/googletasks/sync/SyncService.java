package com.shrugal.googletasks.sync;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.shrugal.googletasks.provider.Lists;
import com.shrugal.googletasks.provider.Tasks;

/**
 * Service to handle Account sync. This is invoked with an intent with action
 * ACTION_AUTHENTICATOR_INTENT. It instantiates the syncadapter and returns its
 * IBinder.
 */
public class SyncService extends Service {	
	/* Finals */
	private static final String URL_TASKS = "https://mail.google.com/tasks/r/d";
	private static final String TAG = "Google Tasks";
	private static final int MAX_ACTIONS_PER_QUERY = 5;
	private static final int MAX_REQUEST_TRYS = 2;
	
	/* Statics */
	private static final Object sSyncAdapterLock = new Object();

	private static SyncAdapter sSyncAdapter = null;
	
	/* Members */
	private AccountManager mAccManager;
	private AndroidHttpClient mHttpClient;
	private long mLastSync = 0;

	/*
	 * {@inheritDoc}
	 */
	@Override
	public void onCreate() {
		Log.i("Google Tasks", "SyncService: onCreate()");
		synchronized (sSyncAdapterLock) {
			if (sSyncAdapter == null) {
				sSyncAdapter = new SyncAdapter(getApplicationContext(), false);
			}
		}
	}

	/*
	 * {@inheritDoc}
	 */
	@Override
	public IBinder onBind(Intent intent) {
		Log.i("Google Tasks", "SyncService: onBind()");
		return sSyncAdapter.getSyncAdapterBinder();
	}
	
	@Override
	public void onDestroy () {
		//if(mHttpClient != null) mHttpClient.close();
	}
	

	private class SyncAdapter extends AbstractThreadedSyncAdapter {
		
		/* Members */
		private Context mContext;

		public SyncAdapter(Context context, boolean autoInitialize) {
			super(context, autoInitialize);
			Log.i("Google Tasks", "SyncAdapter: constructor()");
			
			//Init members
			mContext = context;
			mAccManager = AccountManager.get(context);
			
			//TODO: Wirlich nötig? Bzw. woanders unterbringen (settings zb)
			/*for(Account account: mAccManager.getAccountsByType("com.google")) {
				ContentResolver.setIsSyncable(account, TasksProvider.AUTHORITY, 1);
			}*/
		}

		@Override
		public void onPerformSync(Account account, Bundle extras, String authority,	ContentProviderClient provider, SyncResult syncResult) {
			Log.d("Google Tasks", "onPerformSync()");
			
			String sync_account = PreferenceManager.getDefaultSharedPreferences(mContext).getString("sync_account", "");
			if(!sync_account.equals(account.name)) {
				ContentResolver.setIsSyncable(account, authority, 0);
				Log.i("Google Tasks", "SyncService: Account wrong!");
				return;
			}
			
			Log.i("Google Tasks", "Start syncing ...");
			
			try {
				//Init client
				if(mHttpClient == null) mHttpClient = AndroidHttpClient.newInstance("Google Tasks for Android");
				
				//Init variables
				String userId = "";
				Integer serverTimeOffset = 0;
				ArrayList<JSONObject> lists = new ArrayList<JSONObject>();
				ArrayList<JSONObject> tasks = new ArrayList<JSONObject>();
				
				//Get lists, tasks, user id and server time offset from the server
				userId = getItems(account, serverTimeOffset, lists, tasks);
				
				Log.i("Google Tasks", "Got lists and tasks ...");
				
				if(TextUtils.isEmpty(userId)) {
					Log.i("Google Tasks", "User id is empty!");
					return;				
				}
				
				//Update local and remote lists and tasks
				updateLists(account, lists, userId, serverTimeOffset);
				updateTasks(account, tasks, userId, serverTimeOffset);

				Log.i("Google Tasks", "Lists and tasks updated ...");
				
				//Update last sync date				
				SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(SyncService.this).edit();
				editor.putLong("sync_last_sync", System.currentTimeMillis());
				editor.commit();
				Log.i("Google Tasks", "Sync completed!");
				
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		
		/**
		 * 
		 * @param lists
		 * @param tasks
		 * @return
		 */
		private String getItems (Account account, Integer server_time_offset, ArrayList<JSONObject> lists, ArrayList<JSONObject> tasks) {	
			try {
				//Init variables
				long listId;
				JSONObject task;
				String userId = "";
				
				//Do request
				List<NameValuePair> entity = new ArrayList<NameValuePair>(1);
				entity.add(new BasicNameValuePair("r", new QueryHelper(mLastSync).addGetAction(null, true, true).toString()));				
				JSONObject response = new JSONObject(httpRequest(account, URL_TASKS, entity));
				
				//Get Server time offset, lists and tasks from first list
				server_time_offset = (int) (response.getLong("response_time") - System.currentTimeMillis()/1000);
				JSONArray listsJSON =  response.getJSONArray("lists");
				JSONArray tasksJSON =  response.getJSONArray("tasks");
				
				//Add lists to ArrayList and load tasks from from the other lists
				for(int i=0; i<listsJSON.length(); i++) {
					Log.i("Google Tasks", "getItems(): for(i="+ i +")");
					Log.i("Google Tasks", "-> Tasks: "+ tasksJSON.length());
					listId = QueryHelper.getListId(listsJSON.getJSONObject(i).getString("id"));
					
					//Request tasks if not already done
					if(i == 0) {
						userId = QueryHelper.getUserId(listsJSON.getJSONObject(i).getString("id"));
					} else {
						entity = new ArrayList<NameValuePair>(1);
						entity.add(new BasicNameValuePair("r", new QueryHelper(mLastSync, userId).addGetAction(listId, true, true).toString()));
						tasksJSON = new JSONObject(httpRequest(account, URL_TASKS, entity)).getJSONArray("tasks");
					}
					
					//Add list and tasks
					lists.add(listsJSON.getJSONObject(i));
					for(int j=0; j<tasksJSON.length(); j++) {
						task = tasksJSON.getJSONObject(j);
						if(listId == QueryHelper.getListId(task.getString("id"))) tasks.add(task);
					}
				}
				return userId;
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}
		
		/**
		 * Update lists
		 * @param lists
		 * @param handledIds
		 * @param query
		 * @param creates
		 * @throws JSONException
		 */
		private void updateLists (Account account, ArrayList<JSONObject> lists, String user_id, int server_time_offset) throws JSONException {
			/* ----------- Iterate loaded lists ----------- */
			
			//Init veriables
			QueryHelper query = new QueryHelper(mLastSync, user_id);
			HashMap<Integer, Long> creates = new HashMap<Integer, Long>();
			Iterator<JSONObject> iter = lists.iterator();
			Long[] handledIds = new Long[lists.size()];
			JSONObject currItem;
			long gId, dbItemId = 0, dbLastModified = 0;
			Cursor dbItem;
			ContentValues values = new ContentValues();
			ContentResolver db = mContext.getContentResolver();
			
			//Iterate
			for(int i=0; iter.hasNext(); i++, values.clear()) {
				currItem = iter.next();
				gId = QueryHelper.getListId(currItem.getString("id"));
				dbItem = db.query(Lists.CONTENT_URI, null, Lists.G_ID +" = "+gId, null, null);
				if(dbItem.moveToFirst()) {
					dbItemId = dbItem.getLong(dbItem.getColumnIndex(Lists._ID));
					dbLastModified = dbItem.getLong(dbItem.getColumnIndex(Lists.LAST_MODIFIED));
					if(dbItem.getInt(dbItem.getColumnIndex(Lists.LAST_MODIFIED_TYPE)) == Lists.LAST_MODIFIED_TYPE_LOCAL) dbLastModified += server_time_offset;
				}
				else dbItem = null;

				//List is not in db => create it
				if(dbItem == null) {
					values.put(Lists.G_ID, gId);
					values.put(Lists.NAME, currItem.getString("name"));
					values.put(Lists.LAST_MODIFIED, currItem.getLong("last_modified"));
					values.put(Lists.LAST_MODIFIED_TYPE, Lists.LAST_MODIFIED_TYPE_SERVER);
					Uri result = db.insert(Lists.CONTENT_URI, values);
					handledIds[i] = Long.valueOf(result.getPathSegments().get(1));
				}

				//List is newer than in db => update db
				else if(dbLastModified < currItem.getLong("last_modified")) {
					values.put(Lists.NAME, currItem.getString("name"));
					values.put(Lists.LAST_MODIFIED, currItem.getLong("last_modified"));
					values.put(Lists.LAST_MODIFIED_TYPE, Lists.LAST_MODIFIED_TYPE_SERVER);
					values.put(Lists.DELETED, false);
					db.update(ContentUris.withAppendedId(Lists.CONTENT_URI, dbItemId), values, null, null);
					handledIds[i] = dbItemId;
				}

				//List is older than in db => update server
				else if(dbLastModified > currItem.getLong("last_modified")) {
					query.addUpdateListAction(
							dbItem.getLong(dbItem.getColumnIndex(Lists.G_ID)),
							dbItem.getString(dbItem.getColumnIndex(Lists.NAME)),
							1 == dbItem.getInt((dbItem.getColumnIndex(Lists.DELETED))));
					handledIds[i] = dbItemId;
				}
				
				//They are the same
				else handledIds[i] = dbItemId;
			}

			/* ----------- Iterate local lists ----------- */
			
			//Init variables
			String where = Lists.LAST_MODIFIED_TYPE +" = "+ Lists.LAST_MODIFIED_TYPE_LOCAL; //changed locally
			where += " AND "+ Lists.LAST_MODIFIED +" > "+ mLastSync; //changed since last sync
			where += " AND "+ Lists._ID +" NOT IN ("+ TextUtils.join(",", handledIds) +")"; //not already handled			
			dbItem = db.query(Lists.CONTENT_URI, null, where, null, null);
			
			//Iterate
			while(dbItem.moveToNext()) {
				dbItemId = dbItem.getLong(dbItem.getColumnIndex(Lists._ID));
				if(1 == dbItem.getInt(dbItem.getColumnIndex(Lists.DELETED))) {
					//List is marked as deleted => remove it
					db.delete(ContentUris.withAppendedId(Lists.CONTENT_URI, dbItemId), null, null);
				}
				else {
					//Otherwise => create it on server
					creates.put(
							query.addCreateListAction( dbItem.getString(dbItem.getColumnIndex(Lists.NAME))),
							dbItemId);
				}
			}

			/* ----------- Update on server ----------- */
			int size = query.size();
			if(size > 0) {
				List<NameValuePair> entity = new ArrayList<NameValuePair>(1);
				
				//Page the requests
				for(int i=0; i<size; i+=MAX_ACTIONS_PER_QUERY) {
					entity.clear();
					entity.add(new BasicNameValuePair("r", query.toString(i, MAX_ACTIONS_PER_QUERY)));
					
					//Do request
					String answer = httpRequest(account, URL_TASKS, entity);
					JSONArray results = new JSONObject(answer).getJSONArray("results");
	
					//Handle Answer (Update g_ids in db from created entrys)
					JSONObject line;
					for(int j=0; i<results.length(); j++, values.clear()) {
						line = results.getJSONObject(j);
						if(line.getString("result_type").equals("new_entity")) {
							//get db id and g_id
							dbItemId = creates.get(line.getInt("action_id"));
							gId = Long.getLong(line.getString("new_id").split(":")[1]);
							
							//update db
							values.put(Lists.G_ID, gId);
							mContext.getContentResolver().update(ContentUris.withAppendedId(Lists.CONTENT_URI, dbItemId), values, null, null);
						}
					}
				}
			}
		}
		
		/**
		 * Update tasks
		 * @param tasks
		 * @param handledIds
		 * @param query
		 * @param creates
		 * @throws JSONException
		 */
		private void updateTasks (Account account, ArrayList<JSONObject> tasks, String user_id, int server_time_offset) throws JSONException {			
			//Init veriables
			QueryHelper query = new QueryHelper(mLastSync, user_id);
			HashMap<Integer, Long> creates = new HashMap<Integer, Long>();
			Iterator<JSONObject> iter = tasks.iterator();
			Long[] handledIds = new Long[tasks.size()];
			JSONObject currItem;
			long gId, dbListId = 0, dbItemId = 0, dbLastModified = 0;
			Cursor dbItem = null, dbList = null;
			ContentValues values = new ContentValues();
			ContentResolver db = mContext.getContentResolver();
			
			/* ----------- Iterate loaded tasks ----------- */
			
			//Iterate
			for(int i=0; iter.hasNext(); i++, dbItem=null, values.clear()) {
				//Current item
				currItem = iter.next();
				Log.i(TAG, currItem.getString("name"));
				gId = QueryHelper.getTaskId(currItem.getString("id"));
				
				//Database item
				dbList = db.query(Lists.CONTENT_URI, null, Lists.G_ID +" = "+ QueryHelper.getListId(currItem.getString("id")), null, null);
				if(dbList.moveToFirst()) {
					dbListId = dbList.getLong(dbList.getColumnIndex(Lists._ID));
					dbItem = db.query(Tasks.CONTENT_URI, null, Tasks.G_ID +" = "+ gId +" AND "+ Tasks.LIST_ID +" = "+ dbListId, null, null);
					if(dbItem.moveToFirst()) {
						dbItemId = dbItem.getLong(dbItem.getColumnIndex(Tasks._ID));
						dbLastModified = dbItem.getLong(dbItem.getColumnIndex(Tasks.LAST_MODIFIED));
						if(dbItem.getInt(dbItem.getColumnIndex(Tasks.LAST_MODIFIED_TYPE)) == Tasks.LAST_MODIFIED_TYPE_LOCAL) dbLastModified += server_time_offset;
					}
					else dbItem = null;
				}

				//Task is not in db => create it
				if(dbItem == null) {
					Log.i("Google Tasks", "updateTasks()->for->not in db");
					if(!currItem.has("deleted") || !currItem.getBoolean("deleted")) {
						values.put(Tasks.G_ID, gId);
						values.put(Tasks.LIST_ID, dbListId);
						values.put(Tasks.NAME, currItem.has("name") ? currItem.getString("name") : "");
						values.put(Tasks.NOTES, currItem.has("notes") ? currItem.getString("notes") : "");
						values.put(Tasks.LAST_MODIFIED, currItem.has("last_modified") ? currItem.getLong("last_modified") : null);
						values.put(Tasks.LAST_MODIFIED_TYPE, currItem.has("last_modified") ? Tasks.LAST_MODIFIED_TYPE_SERVER : Tasks.LAST_MODIFIED_TYPE_LOCAL);
						values.put(Tasks.COMPLETED, currItem.has("completed") ? currItem.getBoolean("completed") : false);
						
						Uri result = db.insert(Tasks.CONTENT_URI, values);
						handledIds[i] = Long.valueOf(result.getPathSegments().get(1));
					} else handledIds[i] = -1L;
				}

				//Task is newer than in db => update db
				else if(dbLastModified < currItem.getLong("last_modified")) {
					Log.i("Google Tasks", "updateTasks()->for->newer than in db");
					if(currItem.getBoolean("deleted")) {
						db.delete(ContentUris.withAppendedId(Tasks.CONTENT_URI, dbItemId), null, null);
						handledIds[i] = dbItemId;
					} else {
						values.put(Tasks.LIST_ID, dbListId);
						values.put(Tasks.NAME, currItem.has("name") ? currItem.getString("name") : "");
						values.put(Tasks.NOTES, currItem.has("notes") ? currItem.getString("notes") : "");
						values.put(Tasks.LAST_MODIFIED, currItem.has("last_modified") ? currItem.getLong("last_modified") : null);
						values.put(Tasks.LAST_MODIFIED_TYPE, currItem.has("last_modified") ? Tasks.LAST_MODIFIED_TYPE_SERVER : Tasks.LAST_MODIFIED_TYPE_LOCAL);
						values.put(Tasks.COMPLETED, currItem.has("completed") ? currItem.getBoolean("completed") : false);
						values.put(Tasks.DELETED, currItem.has("deleted") ? currItem.getBoolean("deleted") : false);
						db.update(ContentUris.withAppendedId(Tasks.CONTENT_URI, dbItemId), values, null, null);
						handledIds[i] = dbItemId;
					}
				}
				
				//Task is older than in db => update server
				else if(dbLastModified > currItem.getLong("last_modified")) {
					Log.i("Google Tasks", "updateTasks()->for->older than in db");
					query.addUpdateTaskAction(
							dbItem.getLong(dbItem.getColumnIndex(Tasks.G_ID)),
							dbList.getLong(dbList.getColumnIndex(Lists.G_ID)),
							dbItem.getString(dbItem.getColumnIndex(Tasks.NAME)),
							dbItem.getString(dbItem.getColumnIndex(Tasks.NOTES)),
							1 == dbItem.getInt(dbItem.getColumnIndex(Tasks.COMPLETED)),
							1 == dbItem.getInt(dbItem.getColumnIndex(Tasks.DELETED)));
					handledIds[i] = dbItemId;
				}
				
				//They are the same
				else {
					Log.i("Google Tasks", "updateTasks()->for->they are the same");
					handledIds[i] = dbItemId;
				}
			}

			/* ----------- Iterate local tasks ----------- */
			
			//Get changed tasks from db
			String where = Tasks.LAST_MODIFIED_TYPE +" = "+ Tasks.LAST_MODIFIED_TYPE_LOCAL; //changed locally
			where += " AND "+ Tasks.LAST_MODIFIED +" > "+ mLastSync; //changed since last sync
			where += " AND "+ Tasks._ID +" NOT IN ("+ TextUtils.join(",", handledIds) +")"; //not already handled
			Log.i(TAG, where);
			dbItem = db.query(Tasks.CONTENT_URI, null, where, null, null);
			
			//Iterate
			while(dbItem.moveToNext()) {
				dbItemId = dbItem.getLong(dbItem.getColumnIndex(Tasks._ID));
				if(1 == dbItem.getInt(dbItem.getColumnIndex(Tasks.DELETED))) {
					//Task is marked as deleted => remove it
					db.delete(ContentUris.withAppendedId(Tasks.CONTENT_URI, dbItemId), null, null);
				} else {
					//Otherwise => create it on server
					dbList = db.query(ContentUris.withAppendedId(Lists.CONTENT_URI, dbItem.getLong(dbItem.getColumnIndex(Tasks.LIST_ID))), null, null, null, null);
					if(dbList.moveToFirst()) {
						dbListId = dbList.getLong(dbList.getColumnIndex(Lists.G_ID));
						creates.put(
								query.addCreateTaskAction(
										dbListId,
										dbItem.getString(dbItem.getColumnIndex(Tasks.NAME)),
										dbItem.getString(dbItem.getColumnIndex(Tasks.NOTES)),
										1 == dbItem.getInt(dbItem.getColumnIndex(Tasks.COMPLETED))),
								dbItemId);
					}
				}
			}

			/* ----------- Update on server ----------- */
			int size = query.size();
			Log.i(TAG, "New tasks query size: "+ size);
			if(size > 0) {
				List<NameValuePair> entity = new ArrayList<NameValuePair>(1);
				
				//Page the requests
				for(int i=0; i<size; i+=MAX_ACTIONS_PER_QUERY) {
					entity.clear();
					entity.add(new BasicNameValuePair("r", query.toString(i, MAX_ACTIONS_PER_QUERY)));
				
					//Do request
					String answer = httpRequest(account, URL_TASKS, entity);
					JSONArray results = new JSONObject(answer).getJSONArray("results");
	
					//Handle Answer (Update g_ids in db from created entrys)
					JSONObject line;
					for(int j=0; j<results.length(); j++, values.clear()) {
						line = results.getJSONObject(j);
						if(line.getString("result_type").equals("new_entity")) {
							//get db id and g_id
							dbItemId = creates.get(line.getInt("action_id"));
							gId = QueryHelper.getTaskId(line.getString("new_id"));
							
							//update db
							values.put(Lists.G_ID, gId);
							mContext.getContentResolver().update(ContentUris.withAppendedId(Tasks.CONTENT_URI, dbItemId), values, null, null);
						}
					}
				}
			}
		}
		
		/**
		 * Do a http request and return answer as string
		 * @param url
		 * @param entity
		 * @return
		 */
		private String httpRequest (Account account, String url, List<NameValuePair> entity) {
			try {
				String authToken;
				//Try max two times ...
				for(int i=0; i<MAX_REQUEST_TRYS; i++) {
					//Get token
					authToken = mAccManager.blockingGetAuthToken(account, "goanna_mobile", false);
					if(authToken == null) return null;
					
					Log.i(TAG, "AuthToken: "+ authToken);
					
					//Build request
					HttpPost post = new HttpPost(url);	
					post.addHeader("AT", "1");
					post.addHeader("Cookie", "GTL="+ authToken);
					post.setEntity(new UrlEncodedFormEntity(entity));

					//Do request
					HttpResponse response = mHttpClient.execute(post);
					
					//Process response
					switch(response.getStatusLine().getStatusCode()) {
						case 200:
							BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
							String line;
							StringBuilder builder = new StringBuilder();
							while((line = reader.readLine()) != null) builder.append(line +"\n");
							Log.i(TAG, builder.toString());
							return builder.toString();
						case 403:
							mAccManager.invalidateAuthToken(account.type, authToken);
						default:
							Log.e("Google Tasks", "httpRequest: "+ response.getStatusLine().getStatusCode());
					}
				}
				return null;
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (OperationCanceledException e) {
				e.printStackTrace();
			} catch (AuthenticatorException e) {
				e.printStackTrace();
			}
			return null;
		}
	}
}
