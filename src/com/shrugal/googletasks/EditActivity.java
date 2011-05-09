package com.shrugal.googletasks;

import java.util.Formatter;
import java.util.Locale;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public abstract class EditActivity extends Activity implements OnClickListener {
	
	/* Members */
	protected int mType;
	protected long mId;
	protected Cursor mCursor;
	protected static StringBuilder mSB = new StringBuilder(50);
	protected static Formatter mF = new Formatter(mSB, Locale.getDefault());
	
	/* Form fields */
	protected TextView mNameField;
	protected TextView mNotesField;
	protected Button mSaveButton;
	protected Button mCloseButton;
	
	/**
	 * onCreate
	 */
	public void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//Init Layout
		mNameField = (TextView) findViewById(R.id.EditNameInput);
		mNotesField = (TextView) findViewById(R.id.EditNotesInput);
		mSaveButton = (Button) findViewById(R.id.EditSaveButton);
		mCloseButton = (Button) findViewById(R.id.EditCloseButton);
		mSaveButton.setOnClickListener(this);
		mCloseButton.setOnClickListener(this);
		
		//Set title
		setTitle();
	}
	
	/**
	 * updateFields
	 */
	protected abstract void updateFields ();
	
	private void setTitle () {
		String state = getResources().getText(mId > 0 ? R.string.Edit : R.string.Create).toString();
		String type = getResources().getText(mType).toString();
		
		setTitle(getResources().getText(R.string.app_name) +" - "+ state +" "+ type);
	}

	@Override
	public void onClick(View v) {		
		switch(v.getId()) {
			//Save
			case R.id.EditSaveButton:
				save();
				break;
			//Close
			case R.id.EditCloseButton:
				cancel();
		}
	}
	
	/**
	 * Save
	 */
	protected abstract void save ();
	
	/**
	 * Cancel
	 */
	protected void cancel () {
		setResult(RESULT_CANCELED);
		finish();
	}
	
	/* ------------------------------------------------
	 *                     Utils
	 * ------------------------------------------------*/
	
	protected void setDate (TextView view, long millis) {
		int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR |
		DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_MONTH |
		DateUtils.FORMAT_ABBREV_WEEKDAY;
		
		mSB.setLength(0);
		String dateString = DateUtils.formatDateRange(this, mF, millis, millis, flags).toString();
		view.setText(dateString);
	}
}
