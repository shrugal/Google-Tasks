package com.shrugal.googletasks.tasks;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.SimpleCursorAdapter;

import com.shrugal.googletasks.R;
import com.shrugal.googletasks.R.id;
import com.shrugal.googletasks.R.layout;
import com.shrugal.googletasks.provider.Tasks;

public class TasksFragment extends ListFragment {
	
	private Cursor mCursor;
	private SimpleCursorAdapter mAdapter;
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
	
	public void initList () {
		//Get cursor
		String[] projection = new String[] {Tasks._ID, Tasks.NAME};
		String selection = Tasks.LIST_ID +" = "+ mListId +" AND "+ Tasks.DELETED +" = 0";
		mCursor = getActivity().getContentResolver().query(Tasks.CONTENT_URI, projection, selection, null, null);
		
		//Init adapter
		mAdapter = new SimpleCursorAdapter(getActivity(), R.layout.tasks_list_item, mCursor, 
			new String[] {Tasks.NAME},
			new int[] {R.id.name}
		);
		mAdapter.setViewBinder(new ViewBinder());
		setListAdapter(mAdapter);
		getListView().setOnItemClickListener((TasksActivity) getActivity());
		getListView().setOnCreateContextMenuListener(getActivity());
		
		//TODO: Set the width to the ScrollView's width
		int width = getActivity().getWindowManager().getDefaultDisplay().getWidth();
		getListView().setLayoutParams(new FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT));
	}
	
	private class ViewBinder implements SimpleCursorAdapter.ViewBinder {
		@Override
		public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
			//Checkbox
			if(view instanceof CheckBox) {
				CheckBox cb = (CheckBox) view;
				cb.setChecked(cursor.getInt(columnIndex) == 1);
			}
			return false;
		}		
	}
}