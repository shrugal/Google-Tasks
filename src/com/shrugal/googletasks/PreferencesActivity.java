package com.shrugal.googletasks;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

import com.shrugal.googletasks.provider.TasksProvider;
import com.shrugal.googletasks.sync.ChooseAccountActivity;

public class PreferencesActivity extends PreferenceActivity implements OnPreferenceClickListener {
	
	/* Finals */
	private static final int SYNC_ACCOUNT_AUTH = 0;
	private static final int SYNC_ACCOUNT_CHOOSE = 1;
	private static final String AUTH_TOKEN_TYPE = "cl";
	
	
	public void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		
		findPreference("sync_account").setOnPreferenceClickListener(this);
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		String key = preference.getKey();
		
		if(key.equals("sync_account")) sync_account(); //TODO: Account entfernen
		return true;
	}
	
	@Override
	protected void onActivityResult (int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
		
			//Authentication
			case SYNC_ACCOUNT_AUTH:
				Log.d("Google Tasks", "onActivityResult()->SYNC_ACCOUNT_AUTH");
				if(resultCode == RESULT_OK) {
					Log.d("Google Tasks", "onActivityResult()->SYNC_ACCOUNT_AUTH->OK");
					String name = data.getExtras().getString("account");
					Log.d("Google Tasks", "onActivityResult()->SYNC_ACCOUNT_AUTH->OK->name: "+ name);
					
					//Update settings
					Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
					editor.putString("sync_account", name);
					editor.commit();
					
					//Update isSyncable
					Account[] accounts = AccountManager.get(this).getAccountsByType("com.google");
					for(int i=0; i<accounts.length; i++) ContentResolver.setIsSyncable(accounts[i], TasksProvider.AUTHORITY, (accounts[i].name.equals(name) ? 1 : 0) );
				} else {
					Log.d("Google Tasks", "onActivityResult()->SYNC_ACCOUNT_AUTH->NOT OK");
				}
				break;
				
			//Account selection
			case SYNC_ACCOUNT_CHOOSE:
				if(resultCode == RESULT_OK) {
					String name = data.getExtras().getString("account");
					Account[] accounts = AccountManager.get(this).getAccountsByType("com.google");
					for(int i=0; i<accounts.length; i++) {
						if(accounts[i].name.equals(name)) {
							sync_account(accounts[i]);
							return;
						}
					}
				}
				break;
		}
	}
	
	private void sync_account () {
		Log.d("Google Tasks", "sync_account()");
		Account[] accounts = AccountManager.get(this).getAccountsByType("com.google");
		if(accounts.length == 0) {
		} else if(accounts.length > 1) {
			startActivityForResult(new Intent(this, ChooseAccountActivity.class), SYNC_ACCOUNT_CHOOSE);
		} else {
			sync_account(accounts[0]);
		}
	}
	
	private void sync_account(final Account account) {
		Log.d("Google Tasks", "sync_account(account)");
		//Get permission
		new Thread() {
			@Override
			public void run () {
				try {
					final Bundle answer = AccountManager.get(PreferencesActivity.this).getAuthToken(account, AUTH_TOKEN_TYPE, null, PreferencesActivity.this, null, null).getResult();
					
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Log.d("Google Tasks", "sync_account(account)->runOnUiThread()");
							if(answer.containsKey(AccountManager.KEY_INTENT)) {
								Log.d("Google Tasks", "sync_account(account)->runOnUiThread()->Intent");
								Intent intent = answer.getParcelable(AccountManager.KEY_INTENT);
								int flags = intent.getFlags();
								flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
								intent.setFlags(flags);
								startActivityForResult(intent, SYNC_ACCOUNT_AUTH);
							} else if(answer.containsKey(AccountManager.KEY_AUTHTOKEN)) {
								Log.d("Google Tasks", "sync_account(account)->runOnUiThread()->AuthToken");
								Intent intent = new Intent();
								intent.putExtra("account", answer.getString(AccountManager.KEY_ACCOUNT_NAME));
								onActivityResult(SYNC_ACCOUNT_AUTH, RESULT_OK, intent);
							}
						}
					});
				} catch (Exception e) {
					e.printStackTrace();
					//TODO: Show dialog
					setResult(RESULT_CANCELED);
					finish();
				}
			}
		}.start();
	}
}