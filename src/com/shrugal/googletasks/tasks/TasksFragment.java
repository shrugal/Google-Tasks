package com.shrugal.googletasks.tasks;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.shrugal.googletasks.R;
import com.shrugal.googletasks.provider.Tasks;

public class TasksFragment extends ListFragment {
	
	/* Finals */
	private static final String[] DB_PROJECTION = new String[] {Tasks._ID, Tasks.NAME, Tasks.COMPLETED, Tasks.RANK, Tasks.CHILDS};
	private static final String TAG = "Google Tasks";
	
	private Cursor mCursor;
	private ResourceCursorAdapter mAdapter;
	private long mListId;
	
	@Override
	public void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@Override
	public void onActivityCreated (Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mListId = getArguments().getLong("list_id");
		initList();
	}

	@Override
	public void onPause () {
		super.onPause();
		if(mCursor != null) mCursor.deactivate();
	}
	
	public void onResume () {
		super.onResume();
		if(mCursor != null) mCursor.requery();
	}
	
	public void onDestroy () {
		super.onDestroy();
		if(mCursor != null) mCursor.close();
	}
	
	public long getListId () {
		return mListId;
	}
	
	public void initList () {
		//Get cursor
		String selection = Tasks.LIST_ID +" = "+ mListId +" AND "+ Tasks.DELETED +" = 0";
		mCursor = getActivity().getContentResolver().query(Tasks.CONTENT_URI, DB_PROJECTION.clone(), selection, null, null);
		//Init adapter
		mAdapter = new TasksAdapter(getActivity(), R.layout.tasks_list_item, mCursor, this);
		//mAdapter = new TestAdapter(getActivity(), R.layout.tasks_list_item, mCursor);
		getListView().setAdapter(mAdapter);
		getListView().setOnItemClickListener((TasksActivity) getActivity());
		getListView().setOnCreateContextMenuListener(getActivity());
		
		//TODO: Set the width to the ScrollView's width
		int width = getActivity().getWindowManager().getDefaultDisplay().getWidth();
		getListView().setLayoutParams(new FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT));
	}
	
	private class TestAdapter extends ResourceCursorAdapter {

		public TestAdapter(Context context, int layout, Cursor c) {
			super(context, layout, c);
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			Log.i(TAG, "GetView: "+ position);
			return super.getView(position, convertView, parent);
		}
		
		@Override
		public void bindView(View view, final Context context, Cursor c) {
			String name = c.getString(c.getColumnIndex(Tasks.NAME));
			
			//Name
			TextView text = (TextView) view.findViewById(R.id.name);
			text.setText(name);
		}
		
		@Override
		public void notifyDataSetChanged () {
			Log.i(TAG, "NotifyDataSetChanged()");
			super.notifyDataSetChanged();
		}
		
		@Override
		public void notifyDataSetInvalidated() {
			Log.i(TAG, "NotifyDataSetInvalidated()");
			super.notifyDataSetInvalidated();
		}
	}
}