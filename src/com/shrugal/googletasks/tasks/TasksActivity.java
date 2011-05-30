package com.shrugal.googletasks.tasks;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.shrugal.googletasks.PreferencesActivity;
import com.shrugal.googletasks.R;
import com.shrugal.googletasks.WorkspaceView;
import com.shrugal.googletasks.provider.Lists;
import com.shrugal.googletasks.provider.Tasks;
import com.shrugal.googletasks.provider.TasksProvider;

public class TasksActivity extends FragmentActivity implements OnClickListener, OnItemClickListener, OnCreateContextMenuListener, WorkspaceView.OnWorkspaceListener {
	/* Debug */
	@SuppressWarnings("unused")
	private static final String TAG = "Google Tasks";
	
	/* Finals */
	static final int ACTION_ADD = 0;
	static final int ACTION_EDIT = 1;
	
	/* Members */
	private long[] mListIds;
	private String[] mListNames;
	private int mCurrentList = -1;
	private TextView mTitleField;
	public WorkspaceView mScrollView;
	private LinearLayout mScrollViewContainer;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tasks);
		
		//Init title
		mTitleField = (TextView) findViewById(R.id.TopBarTitle);		
		
		//Init buttons
		findViewById(R.id.ReloadButton).setOnClickListener(this);
		findViewById(R.id.AddButton).setOnClickListener(this);
		findViewById(R.id.TopBarTitle).setOnClickListener(this);
		
		//Init scroll view
		mScrollView = (WorkspaceView) findViewById(R.id.ScrollView);
		mScrollViewContainer = (LinearLayout) findViewById(R.id.ScrollViewContainer);
		mScrollView.setOnWorkspaceListener(this);
		
		//Not first start?
		if(savedInstanceState == null) {
			//Init variables
			long sendedListId = getIntent().getExtras().getLong(Lists._ID, -1);
			Cursor c = getContentResolver().query(Lists.CONTENT_URI, null, null, null, null);
			int count = c.getCount();
			mListIds = new long[count];
			mListNames = new String[count];
			TasksFragment[] fragments = new TasksFragment[count];
			
			Bundle fragmentData;
			for(int i=0; c.moveToNext(); i++) {
				mListIds[i] = c.getLong(c.getColumnIndex(Lists._ID));
				if(mListIds[i] == sendedListId) mCurrentList = i;
				mListNames[i] = c.getString(c.getColumnIndex(Lists.NAME));
				
				fragments[i] = new TasksFragment();
				fragmentData = new Bundle();
				fragmentData.putLong("list_id", mListIds[i]);
				fragments[i].setArguments(fragmentData);
			}
					
			//Init fragments
			if(fragments.length > 0) {
				FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
				for(int i=0; i<fragments.length; i++) {
					transaction.add(R.id.ScrollViewContainer, fragments[i], mListIds[i]+"");
				}
				transaction.commit();
			}
		} else {
			//Restore variables
			mListIds = savedInstanceState.getLongArray("mListIds");
			mListNames = savedInstanceState.getStringArray("mListNames");
			mCurrentList = savedInstanceState.getInt("mCurrentList");
		}
		
		//Set title
		if(mCurrentList != -1) mTitleField.setText(mListNames[mCurrentList]);
	}
	
	@Override
	protected void onSaveInstanceState (Bundle outState) {
		outState.putLongArray("mListIds", mListIds);
		outState.putStringArray("mListNames", mListNames);
		outState.putInt("mCurrentList", mCurrentList);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onClick(View v) {
		switch(v.getId()) {
			//Add
			case R.id.AddButton:
				Long parent = (Long) v.getTag(R.id.parent);
				Long list_id = (Long) v.getTag(R.id.list_id);
				Intent i = new Intent(this, TasksEditActivity.class);
				i.putExtra(Tasks._ID, 0);
				i.putExtra(Tasks.LIST_ID, list_id == null ? mListIds[mCurrentList] : list_id);
				i.putExtra(Tasks.PARENT, parent == null ? 0 : parent);
				startActivityForResult(i, ACTION_ADD);
				break;
			//Reload
			case R.id.ReloadButton:
				Account[] accounts = AccountManager.get(this).getAccountsByType("com.google");
				String account = PreferenceManager.getDefaultSharedPreferences(this).getString("sync_account", "");
				Bundle bundle = new Bundle();
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
				for(int n=0; n<accounts.length; n++) {
					if(accounts[n].name.equals(account)) {
						ContentResolver.requestSync(accounts[n], TasksProvider.AUTHORITY, bundle);
						break;
					}
				}
				break;
			//Checkbox
			case R.id.completedCheckbox:
				Long id = (Long) v.getTag(R.id.id);
				ContentValues values = new ContentValues();
				values.put(Tasks.COMPLETED, ((CheckBox) v).isChecked());
				getContentResolver().update(ContentUris.withAppendedId(Tasks.CONTENT_URI, id), values, null, null);
				break;
			//Title
			case R.id.TopBarTitle:
				finish();
				break;
		}
	}

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		switch(requestCode) {
			case ACTION_ADD:
			case ACTION_EDIT:
				Toast.makeText(this, resultCode == RESULT_OK ? "Saved" : "Cancled", Toast.LENGTH_SHORT).show();
		}
	}
    
    /* -------------------------------------------------
     *                  Options Menu
     * ------------------------------------------------- */
    
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

    /* -------------------------------------------------
     *                  Context Menu
     * -------------------------------------------------*/
    
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
				Intent i = new Intent(this, TasksEditActivity.class);
				i.putExtra(Tasks._ID, info.id);
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
		//Start edit acitivity
		Intent i = new Intent(this, TasksEditActivity.class);
		i.putExtra(Tasks._ID, id);
		startActivityForResult(i, ACTION_EDIT);
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
					values.put(Tasks.DELETED, true);
					values.put(Tasks.LAST_MODIFIED, System.currentTimeMillis());
					getContentResolver().update(ContentUris.withAppendedId(Tasks.CONTENT_URI, mId), values, null, null);
					dialog.cancel();
					Toast.makeText(TasksActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
					break;
				case DialogInterface.BUTTON_NEGATIVE:
					//Cancel dialog
					dialog.cancel();
					break;
			}
		}
	}

	/* ---------------------------------------------------
	 *                  Workspace stuff
	 * --------------------------------------------------- */
	
	@Override
	public void onWorkspaceScrolled(int index) {
		if(index != -1) {
			mCurrentList = Math.max(0, Math.min(mListIds.length-1, index));
			mTitleField.setText(mListNames[mCurrentList]);
		}
	}
	
	@Override
	public void onWorkspaceLayouted () {
		mScrollView.scrollToWorkspace(mCurrentList);
	}
}