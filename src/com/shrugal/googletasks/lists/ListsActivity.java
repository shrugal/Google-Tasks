package com.shrugal.googletasks.lists;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

import com.shrugal.googletasks.PreferencesActivity;
import com.shrugal.googletasks.R;
import com.shrugal.googletasks.R.id;
import com.shrugal.googletasks.R.layout;
import com.shrugal.googletasks.R.menu;
import com.shrugal.googletasks.R.string;
import com.shrugal.googletasks.provider.Lists;
import com.shrugal.googletasks.provider.TasksProvider;
import com.shrugal.googletasks.tasks.TasksActivity;

public class ListsActivity extends ListActivity implements OnClickListener, OnItemClickListener {
	
	/* Finals */
	private static final int ACTION_ADD = 0;
	private static final int ACTION_EDIT = 1;
	
	/* Members */
	private Cursor mCursor;
	private SimpleCursorAdapter mAdapter;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.lists);
		
		//Init list
		String[] projection = new String[] {Lists._ID, Lists.NAME};
		String selection = Lists.DELETED +" = 0";
		mCursor = getContentResolver().query(Lists.CONTENT_URI, projection, selection, null, null);
		startManagingCursor(mCursor);
		
		mAdapter = new SimpleCursorAdapter(this, R.layout.lists_list_item, mCursor, 
			new String[] {Lists.NAME},
			new int[] {R.id.name}
		);
		setListAdapter(mAdapter);
		getListView().setOnItemClickListener(this);
		registerForContextMenu(getListView());
		
		//Init buttons
		findViewById(R.id.ReloadButton).setOnClickListener(this);
		findViewById(R.id.AddButton).setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {		
		switch(v.getId()) {
			//Add
			case R.id.AddButton:
				Intent intent = new Intent(this, ListsEditActivity.class);
				intent.putExtra(Lists._ID, 0);
				startActivityForResult(intent, ACTION_ADD);
				break;
			//Reload
			case R.id.ReloadButton:
				Account[] accounts = AccountManager.get(this).getAccountsByType("com.google");
				String account = PreferenceManager.getDefaultSharedPreferences(this).getString("sync_account", "");
				Bundle bundle = new Bundle();
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
				for(int i=0; i<accounts.length; i++) {
					if(accounts[i].name.equals(account)) {
						ContentResolver.requestSync(accounts[i], TasksProvider.AUTHORITY, bundle);
						break;
					}
				}
				break;
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		switch(requestCode) {
			case ACTION_ADD:
			case ACTION_EDIT:
				Toast.makeText(this, resultCode == RESULT_OK ? "Saved" : "Cancled", Toast.LENGTH_SHORT).show();
				if(resultCode == RESULT_OK)	mAdapter.notifyDataSetChanged();
		}
	}
	
	public boolean onCreateOptionsMenu (Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}
	
	public boolean onOptionsItemSelected (MenuItem item) {
		switch(item.getItemId()) {
			case R.id.SettingsMenuItem:
				startActivity(new Intent(this, PreferencesActivity.class));
				break;
		}
		return true;
	}
	
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.lists_context, menu);
		TextView item = (TextView) ((AdapterContextMenuInfo) menuInfo).targetView.findViewById(R.id.name);
		menu.setHeaderTitle(item.getText());
	}
	
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch(item.getItemId()) {
			case R.id.Edit:
				//Start edit acitivity
				Intent i = new Intent(this, ListsEditActivity.class);
				i.putExtra(Lists._ID, info.id);
				startActivityForResult(i, ACTION_EDIT);
				return true;
			case R.id.Delete:
				//Show a confirmation dialog and delete/abort
				AlertDialog.Builder dialog = new AlertDialog.Builder(this);
				dialog.setCancelable(false)
					  .setMessage(getResources().getString(R.string.areYouSure))
					  .setPositiveButton(android.R.string.yes, new DialogOnClickListener(info.id))
					  .setNegativeButton(android.R.string.no, new DialogOnClickListener(info.id))
					  .create().show();
				return true;
			default:
				return super.onContextItemSelected(item);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> list, View item, int position, long id) {
		Intent i = new Intent(this, TasksActivity.class);
		i.putExtra(Lists._ID, id);
		startActivity(i);
	}
	
	/**
	 * Dialog on click listener
	 * @author Niggo
	 *
	 */
	private class DialogOnClickListener implements DialogInterface.OnClickListener {
		
		private long mId;
		
		public DialogOnClickListener (long id) {
			mId = id;
		}
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch(which) {
				case DialogInterface.BUTTON_POSITIVE:
					//Mark as deleted
					ContentValues values = new ContentValues();
					values.put(Lists.DELETED, true);
					getContentResolver().update(ContentUris.withAppendedId(Lists.CONTENT_URI, mId), values, null, null);
					dialog.cancel();
					Toast.makeText(ListsActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
					break;
				case DialogInterface.BUTTON_NEGATIVE:
					//Cancel dialog
					dialog.cancel();
					break;
			}
		}
	}
}