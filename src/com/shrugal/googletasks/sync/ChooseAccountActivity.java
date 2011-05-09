package com.shrugal.googletasks.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.shrugal.googletasks.R;

public class ChooseAccountActivity extends ListActivity implements OnItemClickListener {
	
	/* Finals */
	private static final int REQUEST_AUTH = 0;
	
	/* Members */
	private Account[] mAccounts;
	
	public void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.prefs_choose_account);
		
		mAccounts = AccountManager.get(this).getAccountsByType("com.google");
		String[] accountNames = new String[mAccounts.length];
		for(int i=0; i<mAccounts.length; i++) accountNames[i] = mAccounts[i].name;
		getListView().setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, accountNames));
		getListView().setOnItemClickListener(this);
	}
	
	public void setAccount(String name) {
		Intent intent = new Intent();
		intent.putExtra("account", name);
		setResult(RESULT_OK, intent);
		finish();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		String name = ((TextView) view.findViewById(android.R.id.text1)).getText().toString();
		setAccount(name);
		
	}
}
