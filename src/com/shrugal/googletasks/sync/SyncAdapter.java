package com.shrugal.googletasks.sync;

import java.io.IOException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.api.client.extensions.android2.AndroidHttp;
import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.http.HttpExecuteIntercepter;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.tasks.v1.Tasks;
import com.google.api.services.tasks.v1.model.TaskList;
import com.google.api.services.tasks.v1.model.TaskLists;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
	
	private static final String TAG = "Google Tasks";
	private static final String AUTH_TOKEN_SCOPE = "cl";
	private static final String AUTH_API_KEY = "AIzaSyDYSbkjZpCApMiNCMKmlAZG-yWNM2x62nQ";
	
	private static final String PREF_ACCOUNT = "sync_account";
	
	private Context mContext;
	private AccountManager mAccManager;
	private HttpTransport mTransport;
	private JsonFactory mJson;
	private String mAuthToken;

	public SyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
		
		//Init members
		mContext = context;
	}

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
		Log.i(TAG, "onPerformSync()");
		
		//Check if its the right account
		String sync_account = PreferenceManager.getDefaultSharedPreferences(mContext).getString(PREF_ACCOUNT, "");
		if(!sync_account.equals(account.name)) {
			Log.i(TAG, "Wrong account: "+account.name);
			ContentResolver.setIsSyncable(account, authority, 0);
			return;
		}
		
		//Init
		Tasks service = init(account);
		
		try {
			TaskLists taskLists = service.tasklists.list().execute();
			
			for (TaskList taskList : taskLists.items) {
				Log.i(TAG, taskList.id);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("deprecation")
	private Tasks init (final Account account) {
		//Init members
		if(mAccManager == null) mAccManager = AccountManager.get(mContext);
		if(mJson == null) mJson = new JacksonFactory();
		if(mTransport == null) {
			mTransport = AndroidHttp.newCompatibleTransport();

			//Setup transport
			mTransport.intercepters.add(new HttpExecuteIntercepter() {
				private boolean mFirstTry = true;
				
				@Override
				public void intercept(HttpRequest request) throws IOException {
					//Set header
					GoogleHeaders headers = new GoogleHeaders();
					if(mAuthToken == null) {
						try { mAuthToken = mAccManager.blockingGetAuthToken(account, AUTH_TOKEN_SCOPE, false); }
						catch (Exception e) { e.printStackTrace(); }
					}
					headers.setGoogleLogin(mAuthToken);
					headers.setDeveloperId(AUTH_API_KEY);
					request.headers = headers;
					
					//Add API key to url
					request.url.put("key", AUTH_API_KEY);
					
					//Set response handler
					request.unsuccessfulResponseHandler = new HttpUnsuccessfulResponseHandler() {
						@Override
						public boolean handleResponse(HttpRequest request, HttpResponse response, boolean allowRetry) throws IOException {
							switch(response.statusCode) {
								case 401:
									/*Log.i(TAG, "Request");
									Log.i(TAG, "===============================");
									for(Map.Entry<String, Object> entry : request.headers.entrySet()) {
										Log.i(TAG, entry.getKey() +": "+ entry.getValue());
									}
									Log.i(TAG, "===============================");
									
									Log.i(TAG, "Response:");
									Log.i(TAG, "===============================");
									Log.i(TAG, response.statusMessage);
									Log.i(TAG, response.parseAsString());
									Log.i(TAG, "===============================");*/
									mAccManager.invalidateAuthToken(account.type, mAuthToken);
									mAuthToken = null;
									if(allowRetry && mFirstTry) {
										mFirstTry = false;
										return true;
									}
									break;
							}
							return false;
						}
					};
				}
			});
		}
		
		//Create tasks service
		return new Tasks(mTransport, mJson);
	}
}
