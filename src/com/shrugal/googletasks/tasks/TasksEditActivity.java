package com.shrugal.googletasks.tasks;

import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;

import com.shrugal.googletasks.EditActivity;
import com.shrugal.googletasks.R;
import com.shrugal.googletasks.R.layout;
import com.shrugal.googletasks.R.string;
import com.shrugal.googletasks.provider.Tasks;

public class TasksEditActivity extends EditActivity {
	
	/* Members */
	private long mListId;
	private long mParent;
	
	@Override
	public void onCreate (Bundle savedInstanceState) {
		mType = R.string.task;
		setContentView(R.layout.tasks_edit);
		super.onCreate(savedInstanceState);
		
		//Init id, list id and parent
		Bundle data = getIntent().getExtras();
		mId = data.getLong(Tasks._ID, 0);
		
		if(mId > 0) {
			mCursor = getContentResolver().query(ContentUris.withAppendedId(Tasks.CONTENT_URI, mId), null, null, null, null);
			if(!mCursor.moveToFirst()) finish();
			mListId = mCursor.getLong(mCursor.getColumnIndex(Tasks.LIST_ID));
			mParent = mCursor.getLong(mCursor.getColumnIndex(Tasks.PARENT));
		} else {
			mListId = data.getLong(Tasks.LIST_ID, 0);
			mParent = data.getLong(Tasks.PARENT, 0);
			if(mListId == 0 && mParent == 0) finish();
		}
		updateFields();
	}

	@Override
	protected void updateFields () {
		if(mId > 0) {
			mNameField.setText(mCursor.getString(mCursor.getColumnIndex(Tasks.NAME)));
			mNotesField.setTextKeepState(mCursor.getString(mCursor.getColumnIndex(Tasks.NOTES)));
		} else {
			mNameField.setText("");
			mNotesField.setText("");
		}
	}

	@Override
	protected void save () {
		long r = -1;
		ContentValues values = new ContentValues();
		values.put(Tasks.NAME, mNameField.getText().toString());
		values.put(Tasks.NOTES, mNotesField.getText().toString());
		values.put(Tasks.LAST_MODIFIED, System.currentTimeMillis());
		values.put(Tasks.LAST_MODIFIED_TYPE, Tasks.LAST_MODIFIED_TYPE_LOCAL);
		values.put(Tasks.DELETED, false);
		
		if(mId > 0) {
			r = getContentResolver().update(ContentUris.withAppendedId(Tasks.CONTENT_URI, mId), values, null, null);
		} else {
			values.put(Tasks.LIST_ID, mListId);
			values.put(Tasks.PARENT, mParent);
			Uri result = getContentResolver().insert(Tasks.CONTENT_URI, values);
			r = Long.valueOf(result.getPathSegments().get(1));
		}
		//TODO: Error handling (form validation)
		setResult(r > 0 ? RESULT_OK : RESULT_CANCELED);
		finish();
	}
}
