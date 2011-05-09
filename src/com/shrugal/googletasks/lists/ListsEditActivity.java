package com.shrugal.googletasks.lists;

import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;

import com.shrugal.googletasks.EditActivity;
import com.shrugal.googletasks.R;
import com.shrugal.googletasks.R.layout;
import com.shrugal.googletasks.R.string;
import com.shrugal.googletasks.provider.Lists;

public class ListsEditActivity extends EditActivity {
	
	/* Members */
	
	public void onCreate (Bundle savedInstanceState) {
		mType = R.string.list;
		setContentView(R.layout.lists_edit);
		super.onCreate(savedInstanceState);
		
		//Init id and list id
		Bundle data = getIntent().getExtras();
		mId = data.getLong(Lists._ID, 0);
		
		if(mId > 0) {
			mCursor = getContentResolver().query(ContentUris.withAppendedId(Lists.CONTENT_URI, mId), null, null, null, null);
			mCursor.moveToFirst();
		}
		updateFields();
	}
	
	@Override
	protected void updateFields () {
		if(mId > 0) {
			mNameField.setText(mCursor.getString(mCursor.getColumnIndex(Lists.NAME)));
		} else {
			mNameField.setText("");
		}
	}
	
	@Override
	protected void save () {
		long r = -1;
		ContentValues values = new ContentValues();
		values.put(Lists.NAME, mNameField.getText().toString());
		values.put(Lists.LAST_MODIFIED, System.currentTimeMillis());
		values.put(Lists.LAST_MODIFIED_TYPE, Lists.LAST_MODIFIED_TYPE_LOCAL);
		
		if(mId > 0) {
			//update
			r = getContentResolver().update(ContentUris.withAppendedId(Lists.CONTENT_URI, mId), values, null, null);
		} else {
			//insert
			Uri result = getContentResolver().insert(Lists.CONTENT_URI, values);
			r = Long.valueOf(result.getPathSegments().get(1));
		}
		//TODO: Error handling (form validation)
		setResult(r > 0 ? RESULT_OK : RESULT_CANCELED);
		finish();
	}
}
