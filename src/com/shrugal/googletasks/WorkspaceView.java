/**
 * 
 */
package com.shrugal.googletasks;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.HorizontalScrollView;

/**
 * @author Niggo
 *
 */
public class WorkspaceView extends HorizontalScrollView {
	/* Debug */
	@SuppressWarnings("unused")
	private static final String TAG = "Google Tasks";
	
	/* Workspaces */
	private OnWorkspaceListener mWorkspaceListener;
	
	/* Scrolling */
	private boolean mIsBeingDragged = false;
	private VelocityTracker mVelocityTracker;
	private int mMinimumVelocity;
	private int mMaximumVelocity;
	private int mActivePointerId;

	public WorkspaceView(Context context) {
        this(context, null);
	}
	
	public WorkspaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
		initScrollView(context);
	}
	
	public WorkspaceView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initScrollView(context);
	}
	
	private void initScrollView(Context context) {
		final ViewConfiguration configuration = ViewConfiguration.get(context);
		mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
	}
	
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if(ev.getAction() == MotionEvent.ACTION_DOWN)
			mActivePointerId = ev.getPointerId(0);
		mIsBeingDragged = super.onInterceptTouchEvent(ev);
		return mIsBeingDragged;
	}
	
	@Override
	public boolean onTouchEvent (MotionEvent ev) {

		if (mVelocityTracker == null) mVelocityTracker = VelocityTracker.obtain();
		mVelocityTracker.addMovement(ev);
		int newPosition = -1;
		
		switch (ev.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:
				mIsBeingDragged = getChildCount() != 0;
				if(mIsBeingDragged)
					mActivePointerId = ev.getPointerId(0);
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				if (mIsBeingDragged) {
					mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
					
					int velocity = (int) mVelocityTracker.getXVelocity(mActivePointerId);
					int width = getWidth();
					int scrollX = getScrollX();
					int position = scrollX / width;

					if (getChildCount() > 0) {
						if ((Math.abs(velocity) > mMinimumVelocity)) {
							newPosition = position + (velocity > 0 ? 0 : 1);
						} else if (scrollX % width < width/2) {
							newPosition = position;
						} else {
							newPosition = position + 1;
						}
						smoothScrollToWorkspace(newPosition);
					}
					
					mIsBeingDragged = false;
					mActivePointerId = -1;
					
					if (mVelocityTracker != null) {
						mVelocityTracker.recycle();
						mVelocityTracker = null;
					}
					
					ev.setAction(MotionEvent.ACTION_POINTER_UP);
				}
				break;
		}
		boolean result =  super.onTouchEvent(ev);
		return result;
	}
	
	/**
	 * Scroll to the workspace with the given index
	 * @param index
	 */
	public void scrollToWorkspace (int index) {
		scrollTo(getWidth() * index, 0);
		performWorkspaceScrolled(index);
	}
	
	/**
	 * Smoothly scroll to the workspace with the given index
	 * @param index
	 */
	public void smoothScrollToWorkspace (int index) {
		smoothScrollTo(getWidth() * index, 0);
		performWorkspaceScrolled(index);
	}
    
    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
    	super.onLayout(changed, l, t, r, b);
    	performWorkspaceLayouted();
    }
	
	/**
	 * Set the WorkspaceListener
	 * @param l
	 */
	public void setOnWorkspaceListener(OnWorkspaceListener l) {
		mWorkspaceListener = l;
	}
	
	/**
	 * Call WorkspaceListener's onWorkspaceScrolled method
	 * @param index
	 */
	public void performWorkspaceScrolled (int index) {
		if(mWorkspaceListener != null)
			mWorkspaceListener.onWorkspaceScrolled(index);
	}
	
	/**
	 * Call WorkspaceListener's onWorkspaceLayouted method
	 */
	public void performWorkspaceLayouted () {
		if(mWorkspaceListener != null)
			mWorkspaceListener.onWorkspaceLayouted();
	}
    
	/**
	 * Listener for Workspace changes
	 * @author Niggo
	 *
	 */
	public interface OnWorkspaceListener {
		public void onWorkspaceScrolled(int index);
		public void onWorkspaceLayouted();
	}    
}
