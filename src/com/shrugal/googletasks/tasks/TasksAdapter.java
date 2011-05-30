package com.shrugal.googletasks.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.shrugal.googletasks.R;
import com.shrugal.googletasks.provider.Tasks;

public class TasksAdapter extends ResourceCursorAdapter implements OnClickListener {
	
	/* - Finals - */
	private static final int INDENT_WIDTH = 30;
	private static final String TAG = "Google Tasks";
	
	/* - Members - */
	private long mListId = -1;
	private TasksFragment mFragment;
	private HashList<Long, Integer> mItems = new HashList<Long, Integer>();
	private ArrayList<Long> mExpanded = new ArrayList<Long>();
	
	/**
	 * Constructor
	 * @param context
	 * @param layout
	 * @param cursor
	 */
	public TasksAdapter(Context context, int layout, Cursor cursor, TasksFragment fragment) {
		super(context, layout, cursor);
		mFragment = fragment;
		mListId = fragment.getListId();
		init(context, cursor, true);
	}
	
	@Override
	protected void init(Context context, Cursor c, boolean autoRequery) {
		if(mListId < 0 || mItems == null) return;
		super.init(context, c, autoRequery);
	}
	
	@Override
	public void changeCursor(Cursor c) {
		if(c == getCursor()) return;
		mItems.clear();
		super.changeCursor(c);
	}
	
	@Override
	public int getCount() {
		return mItems.size();
	}
	
	@Override
	public Object getItem(int position) {
        if(position > getCount()-1) return null;
        return super.getItem(mItems.get(position));
    }
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
        if(position > getCount()-1) return null;
		return super.getView(mItems.get(position), convertView, parent);
	}
	
	@Override
	public void notifyDataSetChanged () {
		addItems(getCursor(), 0L);
		super.notifyDataSetChanged();
	}
	
	@Override
	public void notifyDataSetInvalidated() {
		mItems.clear();
		super.notifyDataSetInvalidated();
	}
	
	/**
	 * Toggles the given item => makes it's childs visible or invisible
	 * @param id
	 * @return the new expanded state
	 */
	public boolean toggleItem (Long id) {
		if(!mItems.containsKey(id)) return false;
		boolean expand;
		
		if(expand = !mExpanded.contains(id)) {
			addItems(getCursor(), id);
			mExpanded.add(id);
		} else {
			removeItems(getCursor(), id);
			mExpanded.remove(id);
		}
		
		super.notifyDataSetChanged();
		return expand;
	}
	
	private int addItems (Cursor c, long parent) {
		return addItems(c, parent, true);
	}
	
	/**
	 * Adds all childs of parent to the list of visible items => makes them visible
	 * @param c
	 * @param parent
	 * @return affected items count
	 */
	private int addItems (Cursor c, long parent, boolean preservePosition) {
		if(c != null) {
			int oldPos = c.getPosition();			
			int idIndex = c.getColumnIndexOrThrow(Tasks._ID);			
			int rankIndex = c.getColumnIndexOrThrow(Tasks.RANK);
			int position = mItems.containsKey(parent) ? mItems.indexOf(parent) : -1;
			int start = position;
			int startRank, childRank;
			Long childId;
			
			if(parent == 0L) {
				startRank = 0;
				c.moveToFirst();
				c.moveToPrevious();
			} else if(position > -1) {
				c.moveToPosition(mItems.get(position));
				startRank = c.getInt(rankIndex)+1;
			} else return 0;
			
			while(c.moveToNext()) {
				childRank = c.getInt(rankIndex);
				childId = c.getLong(idIndex);
				
				if(childRank == startRank) {
					mItems.add(++position, childId, c.getPosition());
					if(mExpanded.contains(childId)) position += addItems(c, childId, false);
				} else if(childRank < startRank) break;
			}
			if(preservePosition) c.moveToPosition(oldPos);
			else c.moveToPrevious();
			return position - start;
		}
		return 0;
	}
	
	/**
	 * Removes all childs of parent from the list of visible items => makes them invisible
	 * @param c
	 * @param parent
	 */
	private void removeItems (Cursor c, long parent) {
		if(c != null) {
			int oldPos = c.getPosition();
			int idIndex = c.getColumnIndexOrThrow(Tasks._ID);
			int rankIndex = c.getColumnIndexOrThrow(Tasks.RANK);
			int startRank, childRank;
			
			if(parent == 0L) {
				startRank = 0;
				c.moveToFirst();
				c.moveToPrevious();
			} else if(mItems.containsKey(parent)) {
				c.moveToPosition(mItems.get(parent));
				startRank = c.getInt(rankIndex)+1;
			} else return;
			
			while(c.moveToNext()) {
				childRank = c.getInt(rankIndex);
				if(childRank < startRank) break;
				
				mItems.remove(c.getLong(idIndex));
			}
			c.moveToPosition(oldPos);
			Log.i(TAG, "---------");
		}
	}
	
	/*
	private void recoverItems (Cursor c) {
		if(c != null) {
			int oldPos = c.getPosition();
			int idIndex = c.getColumnIndexOrThrow(Tasks._ID);
			Long id;
			
			c.moveToFirst(); c.moveToPrevious();
			
			while(c.moveToNext()) {
				id = c.getLong(idIndex);
				if(mItems.containsKey(id)) mItems.put(id, c.getPosition());
			}
			c.moveToPosition(oldPos);
		}
	}
	*/

	@Override
	public void bindView(View view, final Context context, Cursor c) {
		long id = c.getLong(c.getColumnIndex(Tasks._ID));
		String name = c.getString(c.getColumnIndex(Tasks.NAME));
		int rank = c.getInt(c.getColumnIndex(Tasks.RANK));
		int childs = c.getInt(c.getColumnIndex(Tasks.CHILDS));
		boolean completed = c.getInt(c.getColumnIndex(Tasks.COMPLETED)) == 1;
		
		//Indent
		View indent = view.findViewById(R.id.indent);
		indent.setLayoutParams(new LinearLayout.LayoutParams(rank*INDENT_WIDTH, LinearLayout.LayoutParams.MATCH_PARENT));
		
		//Checkbox
		CheckBox checkbox = (CheckBox) view.findViewById(R.id.completedCheckbox);
		checkbox.setTag(R.id.id, id);
		if(checkbox.isChecked() != completed) checkbox.setChecked(completed);
		checkbox.setOnClickListener((TasksActivity) mFragment.getActivity());
		
		//Name
		TextView text = (TextView) view.findViewById(R.id.name);
		text.setText(name);
		
		//Expander
		ImageButton expander = (ImageButton) view.findViewById(R.id.ExpanderButton);
		expander.setTag(R.id.id, id);
		if(childs > 0) {
			expander.setVisibility(View.VISIBLE);
			expander.setOnClickListener(this);
			expander.setImageResource(mExpanded.contains(id) ? R.drawable.ic_expander_open : R.drawable.ic_expander_closed);
		} else {
			expander.setVisibility(View.GONE);
		}
		
		//Add
		ImageButton add = (ImageButton) view.findViewById(R.id.AddButton);
		add.setTag(R.id.parent, id);
		add.setTag(R.id.list_id, mListId);
		add.setOnClickListener((TasksActivity) mFragment.getActivity());
	}

	@Override
	public void onClick(View v) {
		switch(v.getId()) {
			case R.id.ExpanderButton:
				Log.i(TAG, "click!");
				toggleItem((Long) v.getTag(R.id.id));
		}
	}
	
	/**
	 * Combines a list and a map by organizing the map's keys in the list
	 * @author Niggo
	 *
	 * @param <K>
	 * @param <V>
	 */
	private class HashList<K, V> {		
		private List<K> mList = new ArrayList<K>();
		private Map<K, V> mMap = new HashMap<K, V>();
		
		/**
		 * 
		 * @param index
		 * @param key
		 * @param value
		 */
		public void add (int index, K key, V value) {
			int i;
			if((i = indexOf(key)) >= 0) {
				mList.remove(i);
				if(i < index) index--;
			}
			mList.add(index, key);
			mMap.put(key, value);
		}
		
		/**
		 * 
		 * @param key
		 * @param value
		 */
		public void put (K key, V value) {
			add(size(), key, value);
		}
		
		/**
		 * 
		 * @return
		 */
		public int size() {
			return mList.size();
		}
		
		/**
		 * 
		 * @param index
		 * @return
		 */
		public V get (int index) {
			return mMap.get(mList.get(index));
		}
		
		/**
		 * 
		 * @param key
		 * @return
		 */
		public V get (K key) {
			return mMap.get(key);
		}
		
		/**
		 * 
		 * @param key
		 * @return
		 */
		public boolean containsKey (K key) {
			return mMap.containsKey(key);
		}
		
		/**
		 * 
		 * @param value
		 * @return
		 */
		public boolean containsValue (V value) {
			return mMap.containsValue(value);
		}
		
		/**
		 * 
		 */
		public void clear () {
			mList.clear();
			mMap.clear();
			Log.i(TAG, "HashList.clear()");
		}
		
		/**
		 * 
		 * @param key
		 * @return
		 */
		public int indexOf (K key) {
			return mList.indexOf(key);
		}
		
		/**
		 * 
		 * @param key
		 * @return
		 */
		public V remove (K key) {
			mList.remove(key);
			return mMap.remove(key);
		}
		
		/**
		 * 
		 * @param position
		 * @return
		 */
		public V remove (int position) {
			return remove(mList.get(position));
		}
		
		@Override
		public String toString () {
			StringBuffer s = new StringBuffer("[");
			Iterator<K> iter = mList.iterator();
			K id;
			while(iter.hasNext()) {
				id = iter.next();
				s.append(id +" => "+ mMap.get(id));
				if(iter.hasNext()) s.append(", ");
			}
			s.append("]");
			return s.toString();
		}
	}
}