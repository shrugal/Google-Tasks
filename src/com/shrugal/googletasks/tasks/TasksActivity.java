package com.shrugal.googletasks.tasks;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
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
	static final int DRAG_SCROLL_ZONE = 16;
	static final int DRAG_SCROLL_TIMEOUT_HORIZONTAL = 1000;
	static final int DRAG_SCROLL_TIMEOUT_VERTICAL = 20;
	
	/* Members */
	private long[] mListIds;
	private String[] mListNames;
	private int mCurrentList = -1;
	private TextView mTitleField;
	public WorkspaceView mScrollView;
	private LinearLayout mScrollViewContainer;
	private TasksFragment[] mFragments;
	private FragmentManager mFragmentManager;
	
	/* Drag and drop */
	private boolean mDragging = false;
	private boolean mDragCancelDispatched = false;
	private boolean mDragAllowScrolling = false;
	private ImageView mDragImage;
	private View mDragHoverView, mDragView, mDragDivider;
	private long mDragId = 0;
	private float mDragX, mDragY, mDragOffsetX, mDragOffsetY;
	private MotionEvent mDragEvent;
	private PendingCheckForHorizontalScroll mPendingCheckForHorizontalScroll;
	private PendingCheckForVerticalScroll mPendingCheckForVerticalScroll;
	private Handler mHandler = new Handler();
	
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
			mFragments = new TasksFragment[count];
			
			Bundle fragmentData;
			for(int i=0; c.moveToNext(); i++) {
				mListIds[i] = c.getLong(c.getColumnIndex(Lists._ID));
				if(mListIds[i] == sendedListId) mCurrentList = i;
				mListNames[i] = c.getString(c.getColumnIndex(Lists.NAME));
				
				mFragments[i] = new TasksFragment();
				fragmentData = new Bundle();
				fragmentData.putLong("list_id", mListIds[i]);
				mFragments[i].setArguments(fragmentData);
			}
					
			//Init fragments
			mFragmentManager = getSupportFragmentManager();
			if(mFragments.length > 0) {
				FragmentTransaction transaction = mFragmentManager.beginTransaction();
				for(int i=0; i<mFragments.length; i++) {
					transaction.add(R.id.ScrollViewContainer, mFragments[i], mListIds[i]+"");
				}
				transaction.commit();
			}
		} else {
			//Restore variables
			mListIds = savedInstanceState.getLongArray("mListIds");
			mListNames = savedInstanceState.getStringArray("mListNames");
			mCurrentList = savedInstanceState.getInt("mCurrentList");
			
			//Restore fragments
			mFragmentManager = getSupportFragmentManager();
			mFragments = new TasksFragment[savedInstanceState.getInt("fragmentCount")];
			
			for(int i=0; savedInstanceState.containsKey("fragment"+ i) && i<mFragments.length; i++) {
				mFragments[i] = (TasksFragment) mFragmentManager.getFragment(savedInstanceState, "fragment"+ i);
			}
		}
		
		//Set title
		if(mCurrentList != -1) mTitleField.setText(mListNames[mCurrentList]);
	}
	
	@Override
	protected void onSaveInstanceState (Bundle outState) {
		//Save variables
		outState.putLongArray("mListIds", mListIds);
		outState.putStringArray("mListNames", mListNames);
		outState.putInt("mCurrentList", mCurrentList);
		
		//Save fragments
		int i = 0;
		for(; i<mFragments.length; i++)  mFragmentManager.putFragment(outState, "fragment"+ i, mFragments[i]);
		outState.putInt("fragmentCount", mFragments.length);
		
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
	 *				  Options Menu
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
	 *				  Context Menu
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
	
	@Override
	public boolean dispatchTouchEvent (MotionEvent ev) {
		mDragX = ev.getX();
		mDragY = ev.getY();
		
		if(mDragging && mDragImage != null) {
			if(mDragEvent == null) {
				mDragEvent = MotionEvent.obtain(ev);
				mDragEvent.setAction(MotionEvent.ACTION_CANCEL);
			}
			boolean handled = onTouchEvent(ev);
			if(!mDragCancelDispatched) {
				mDragCancelDispatched = true;
				ev.setAction(MotionEvent.ACTION_CANCEL);
			}
			if(handled) return true;			
		}
		
		return super.dispatchTouchEvent(ev);
	}

	@Override
	public boolean onTouchEvent (MotionEvent ev) {
		if(mDragging && mDragImage != null) {
			switch(ev.getAction()) {
				case MotionEvent.ACTION_MOVE:
					drag();
					break;
				case MotionEvent.ACTION_UP:
					drop();
				case MotionEvent.ACTION_CANCEL:
					stopDrag();
					break;
			}
			return true;
		}
		return false;
	}
	
	public void startDrag (View item, long id) {
		if(item == null) return;
		if(mDragging || mDragImage != null) stopDrag();
		
		mDragOffsetX = mDragX;
		mDragOffsetY = item.getHeight()/2;
		
		//Create bitmap from item
		item.setDrawingCacheEnabled(true);
		Bitmap bitmap = Bitmap.createBitmap(item.getDrawingCache());
		
		WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
		layoutParams.gravity = Gravity.TOP;
		layoutParams.x = (int) (mDragX - mDragOffsetX);
		layoutParams.y = (int) (mDragY - mDragOffsetY);

		layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
		layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
		layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
				| WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
				| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
				| WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
				| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
		layoutParams.format = PixelFormat.TRANSLUCENT;
		layoutParams.windowAnimations = 0;
		
		ImageView v = new ImageView(this);
		v.setImageBitmap(bitmap);	  

		WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		mWindowManager.addView(v, layoutParams);
		
		//Drag stuff
		mDragId = id;
		mDragView = item;
		mDragImage = v;
		mDragging = true;
		mDragAllowScrolling = false;
		mDragCancelDispatched = false;
		if(mDragEvent != null) {
			mDragEvent.setLocation(mDragX, mDragY);
			super.dispatchTouchEvent(mDragEvent);
		}
		if(mPendingCheckForHorizontalScroll != null) mHandler.removeCallbacks(mPendingCheckForHorizontalScroll);
		mPendingCheckForHorizontalScroll = null;
		//item.setVisibility(View.INVISIBLE);
	}
	
	/**
	 * Move the dragged item
	 * @param x
	 * @param y
	 */
	private void drag() {
		if (mDragging && mDragImage != null) {
			//Move drag view
			WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) mDragImage.getLayoutParams();
			layoutParams.x = (int) (mDragX - mDragOffsetX);
			layoutParams.y = (int) (mDragY - mDragOffsetY);
			WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
			mWindowManager.updateViewLayout(mDragImage, layoutParams);
			
			//Check for horizontal scrolling
			int width = mScrollView.getWidth();
			int zone = width/DRAG_SCROLL_ZONE;
			boolean inZone = mDragX < zone || mDragX > width - zone;			
			if(mDragAllowScrolling) {
				if(inZone && mPendingCheckForHorizontalScroll == null) {
					mPendingCheckForHorizontalScroll = new PendingCheckForHorizontalScroll();
					mHandler.post(mPendingCheckForHorizontalScroll);
				} else if(!inZone && mPendingCheckForHorizontalScroll != null) {
					mHandler.removeCallbacks(mPendingCheckForHorizontalScroll);
					mPendingCheckForHorizontalScroll = null;
				}
			} else if(!inZone) mDragAllowScrolling = true;
			
			//Check for vertial scrolling
			ListView list = mFragments[mCurrentList].getListView();
			int[] listOffset = new int[2];
			list.getLocationOnScreen(listOffset);
			int listY = (int) (mDragY - listOffset[1]);
			int height = mScrollView.getHeight();
			zone = height/DRAG_SCROLL_ZONE;
			inZone = listY > 0 && listY < height && (listY < zone || listY > height - zone);
			
			if(inZone && mPendingCheckForVerticalScroll == null) {
				mPendingCheckForVerticalScroll = new PendingCheckForVerticalScroll();
				mHandler.post(mPendingCheckForVerticalScroll);
			} else if(!inZone && mPendingCheckForVerticalScroll != null) {
				mHandler.removeCallbacks(mPendingCheckForVerticalScroll);
				mPendingCheckForVerticalScroll = null;
			}
			
			//Hover item
			int position;
			boolean hoverItem = false;
			if((position = list.pointToPosition(0, listY)) != ListView.INVALID_POSITION) {
				View divider, child = (View) list.getChildAt(position - list.getFirstVisiblePosition());
				if(child != null) {
					hoverItem = true;
					double childZone = ((double) (listY - child.getTop())) / child.getHeight();
					
					if(childZone < 0.25) {
						//Top
						if(mDragHoverView != null) {
							mDragHoverView.setSelected(false);
							mDragHoverView = null;
						}
						divider = ((ViewGroup) child).findViewById(R.id.dividerTop);
						if(divider != mDragDivider) {
							if(mDragDivider != null) mDragDivider.setSelected(false);
							mDragDivider = divider;
							divider.setSelected(true);
						}
					} else if (childZone < 0.75) {
						//Middle
						if(child != mDragHoverView) {
							if(mDragHoverView != null) mDragHoverView.setSelected(false);
							mDragHoverView = child;
							child.setSelected(true);
						}
						if(mDragDivider != null) {
							mDragDivider.setSelected(false);
							mDragDivider = null;
						}
					} else {
						//Bottom
						if(mDragHoverView != null) {
							mDragHoverView.setSelected(false);
							mDragHoverView = null;
						}
						divider = ((ViewGroup) child).findViewById(R.id.dividerBottom);
						if(divider != mDragDivider) {
							if(mDragDivider != null) mDragDivider.setSelected(false);
							mDragDivider = divider;
							divider.setSelected(true);
						}
					}
				}
			}
			
			if(!hoverItem){
				if(mDragHoverView != null) {
					mDragHoverView.setSelected(false);
					mDragHoverView = null;
				}
				if(mDragDivider != null) {
					mDragDivider.setSelected(false);
					mDragDivider = null;
				}
			}
		}
	}
	
	/**
	 * Stop a drag
	 * @param itemIndex
	 */
	private void stopDrag () {
		if (mDragging && mDragImage != null) {
			mDragImage.setVisibility(View.GONE);
			WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
			windowManager.removeView(mDragImage);
			mDragImage.setImageDrawable(null);
		}
		mDragId = 0;
		mDragImage = null;
		mDragging = false;
		mDragAllowScrolling = false;
		if(mPendingCheckForHorizontalScroll != null) mHandler.removeCallbacks(mPendingCheckForHorizontalScroll);
		mPendingCheckForHorizontalScroll = null;
		mDragCancelDispatched = false;
		if(mDragView != null) mDragView.setVisibility(View.VISIBLE);
		mDragView = null;
		if(mDragHoverView != null) mDragHoverView.setSelected(false);
		mDragHoverView = null;
		if(mDragDivider != null) mDragDivider.setSelected(false);
		mDragDivider = null;
	}
	
	private void drop () {
		if(mDragId == 0) return;
		int[] viewOffset = new int[2];
		ContentValues values = new ContentValues();
		
		//On title field?
		mTitleField.getLocationOnScreen(viewOffset);
		if(mDragX > viewOffset[0] && mDragX < viewOffset[0] + mTitleField.getWidth() &&
		   mDragY > viewOffset[1] && mDragY < viewOffset[1] + mTitleField.getHeight()) {
			values.put(Tasks.LIST_ID, mListIds[mCurrentList]);
			
			try {
				getContentResolver().update(ContentUris.withAppendedId(Tasks.CONTENT_URI, mDragId), values, null, null);
			} catch (IllegalArgumentException e) {
				Log.i(TAG, e.getMessage());
				e.printStackTrace();
			}
			return;
		}
		
		//On list?
		ListView list = mFragments[mCurrentList].getListView();
		list.getLocationOnScreen(viewOffset);

		if(mDragX > viewOffset[0] && mDragX < viewOffset[0] + list.getWidth() &&
		   mDragY > viewOffset[1] && mDragY < viewOffset[1] + list.getHeight()) {
			
			int position, listY = (int) (mDragY - viewOffset[1]);			
			values.put(Tasks.LIST_ID, mListIds[mCurrentList]);
			
			Long expandId = null;
			if((position = list.pointToPosition(0, listY)) != ListView.INVALID_POSITION) {
				View child = list.getChildAt(position - list.getFirstVisiblePosition());
				double childZone = ((double) (listY - child.getTop())) / child.getHeight();
				
				if(childZone < 0.25) {
					//Top
					values.put(Tasks.PARENT, (Long) child.getTag(R.id.parent));
					values.put(Tasks.FOLLOWER, (Long) child.getTag(R.id.id));
				} else if (childZone < 0.75) {
					//Middle
					expandId = (Long) child.getTag(R.id.id);
					values.put(Tasks.PARENT, expandId);
				} else {
					//Bottom
					values.put(Tasks.PARENT, (Long) child.getTag(R.id.parent));
					values.put(Tasks.PREVIOUS, (Long) child.getTag(R.id.id));
				}
			}
			try {
				getContentResolver().update(ContentUris.withAppendedId(Tasks.CONTENT_URI, mDragId), values, null, null);
				if(expandId != null) ((TasksAdapter) list.getAdapter()).expandItem(expandId);
			} catch (IllegalArgumentException e) {
				Log.i(TAG, e.getMessage());
				e.printStackTrace();
			}
			return;
		}
	}
	
	/**
	 * 
	 * @author Niggo
	 *
	 */
	private class PendingCheckForHorizontalScroll implements Runnable {
		@Override
		public void run() {
			int width = mScrollView.getWidth();
			int zone = width/DRAG_SCROLL_ZONE;
			
			if(mPendingCheckForHorizontalScroll == this) {
				if(mDragX < zone) {
					//Left zone
					if(mCurrentList > 0) mScrollView.smoothScrollToWorkspace(mCurrentList-1);
				} else if (mDragX > width - zone) {
					//Right zone
					if(mCurrentList < mListIds.length-1) mScrollView.smoothScrollToWorkspace(mCurrentList+1);
				} else {
					mHandler.removeCallbacks(this);
					mPendingCheckForHorizontalScroll = null;
					return;
				}
				mHandler.postDelayed(this, DRAG_SCROLL_TIMEOUT_HORIZONTAL);
			}
		}
	}
	
	/**
	 * 
	 * @author Niggo
	 *
	 */
	private class PendingCheckForVerticalScroll implements Runnable {
		@Override
		public void run() {
			ListView list = mFragments[mCurrentList].getListView();
			int height = list.getHeight();
			int zone = height/DRAG_SCROLL_ZONE;
			
			if(mPendingCheckForVerticalScroll == this) {
				int[] listOffset = new int[2];
				list.getLocationOnScreen(listOffset);
				int listY = (int) (mDragY - listOffset[1]);
				int position;
				View item;
				
				if(listY > 0 && listY < height) {
					if(listY < zone) {
						//Top zone
						list.smoothScrollBy(-20, DRAG_SCROLL_TIMEOUT_VERTICAL);
					} else if (listY > height - zone) {
						//Bottom zone
						list.smoothScrollBy(20, DRAG_SCROLL_TIMEOUT_VERTICAL);
					} else {
						mHandler.removeCallbacks(this);
						mPendingCheckForHorizontalScroll = null;
						return;
					}
					mHandler.postDelayed(this, DRAG_SCROLL_TIMEOUT_VERTICAL);
				}
			}
		}
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
	 *				  Workspace stuff
	 * --------------------------------------------------- */
	
	@Override
	public void onWorkspaceScrolled(int index) {
		if(index != -1) {
			mCurrentList = Math.max(0, Math.min(mListIds.length-1, index));
			mTitleField.setText(mListNames[mCurrentList]);
			//TODO: enable and disable fragments
		}
	}
	
	@Override
	public void onWorkspaceLayouted () {
		mScrollView.scrollToWorkspace(mCurrentList);
	}
}