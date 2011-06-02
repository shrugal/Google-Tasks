package com.shrugal.googletasks.tasks;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.shrugal.googletasks.R;
import com.shrugal.googletasks.provider.Tasks;

public class TasksFragment extends ListFragment implements OnLongClickListener {
	
	/* Finals */
	private static final String[] DB_PROJECTION = new String[] {Tasks._ID, Tasks.NAME, Tasks.COMPLETED, Tasks.PARENT, Tasks.RANK, Tasks.CHILDS};
	private static final String TAG = "Google Tasks";
	
	/* Members */
	private TasksAdapter mAdapter;
	private long mListId;
	private long[] mExpanded = null;
	
	@Override
	public void onCreate (Bundle savedInstanceState) {
		if(savedInstanceState != null) {
			mExpanded = savedInstanceState.getLongArray("expanded");
		}
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
		if(mAdapter != null && mAdapter.getCursor() != null) mAdapter.getCursor().deactivate();
	}
	
	public void onResume () {
		super.onResume();
		if(mAdapter != null && mAdapter.getCursor() != null) mAdapter.getCursor().requery();
	}
	
	public void onDestroy () {
		super.onDestroy();
		if(mAdapter != null && mAdapter.getCursor() != null) mAdapter.getCursor().close();
	}
	
	@Override
	public void onSaveInstanceState (Bundle outState) {
		outState.putLongArray("expanded", mAdapter.getExpanded());
	}
	
	public long getListId () {
		return mListId;
	}
	
	public void initList () {
		//Get cursor
		String selection = Tasks.LIST_ID +" = "+ mListId +" AND "+ Tasks.DELETED +" = 0";
		Cursor c = getActivity().getContentResolver().query(Tasks.CONTENT_URI, DB_PROJECTION.clone(), selection, null, null);
		//Init adapter
		mAdapter = new TasksAdapter(getActivity(), R.layout.tasks_list_item, c, mExpanded, this);
		ListView list = getListView();
		list.setAdapter(mAdapter);
		list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		list.setOnItemClickListener((TasksActivity) getActivity());
		list.setOnCreateContextMenuListener(getActivity());
		
		//TODO: Set the width to the ScrollView's width
		int width = getActivity().getWindowManager().getDefaultDisplay().getWidth();
		getListView().setLayoutParams(new FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT));
	}

	@Override
	public boolean onLongClick(View v) {
		switch(v.getId()) {
			case R.id.completedCheckbox:
				if(mAdapter != null) {
					Long id = (Long) v.getTag(R.id.id);
					View item = (View) v.getTag(R.id.parent);
					
					((TasksActivity) getActivity()).startDrag(item, id);
					mAdapter.collapseItem(id);
					return true;
				}
				break;
		}
		return false;
	}
	
	/*
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
	*/
}