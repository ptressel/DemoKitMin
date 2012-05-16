/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.DemoKitMin;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;

public class DemoKitActivity extends Activity implements Runnable {
	protected static final String TAG = "DemoKitActivity";

	private static final String ACTION_USB_PERMISSION = "com.google.android.DemoKitMin.action.USB_PERMISSION";

    /** How long to wait between checks for input from the Arduino. */
    public static final int INPUT_RETRY_WAIT = 100;
    /** Max expected length of a single message from the Arduino. */
    public static final int MAX_MESSAGE_LEN = 1024;
    /** Size of buffer for reads from USB. */
    public static final int BUFFER_LEN = 16384;
    // Chars with meaning in our messages.  These are 8-bit ASCII bytes.
    public static final byte CR = 0x0D;
    public static final byte LF = 0x0A;
    public static final byte AMP = '&';
    public static final byte UPPER_I = 'I';
    public static final byte LOWER_I = 'i';
    public static final byte COMMA = ',';
    
	private UsbManager mUsbManager;
	private PendingIntent mPermissionIntent;
	private boolean mPermissionRequestPending;

	UsbAccessory mAccessory;
	ParcelFileDescriptor mFileDescriptor;
	FileInputStream mInputStream;
	FileOutputStream mOutputStream;
	
	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
            Log.d(TAG, timestamp() + "In BroadcastReceiver onReceive");
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = UsbManager.getAccessory(intent);
					if (intent.getBooleanExtra(
							UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						openAccessory(accessory);
					} else {
						Log.d(TAG, timestamp() + "permission denied for accessory "
								+ accessory);
					}
					mPermissionRequestPending = false;
				}
			} else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = UsbManager.getAccessory(intent);
				if (accessory != null && accessory.equals(mAccessory)) {
					closeAccessory();
				}
			}
		}
	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        Log.d(TAG, timestamp() + "In onCreate");
        
        setContentView(R.layout.no_device);

		mUsbManager = UsbManager.getInstance(this);
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);
        
		// Any reason we can't call this in onResume?
		mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
		if (mAccessory != null) {
            Log.d(TAG, timestamp() + "getLastNonConfigurationInstance returned non-null");
            // onResume is going to do this.
            //openAccessory(mAccessory);
		} else {
            Log.d(TAG, timestamp() + "getLastNonConfigurationInstance returned null");
		}
		/*
		if (getLastNonConfigurationInstance() != null) {
            Log.d(TAG, timestamp() + "getLastNonConfigurationInstance returned non-null");
			mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
			openAccessory(mAccessory);
		} else {
            Log.d(TAG, timestamp() + "getLastNonConfigurationInstance returned null");
        }
        if (mAccessory != null) {
            Log.d(TAG, timestamp() + "mAccessory is non-null");
        } else {
            Log.d(TAG, timestamp() + "mAccessory is null");
        }
        */
		
		//setContentView(R.layout.no_device);
		//enableControls(mAccessory != null);
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mAccessory != null) {
            Log.d(TAG, timestamp() + "onRetainNonConfigurationInstance found existing mAccessory");
			return mAccessory;
		} else {
		    // This seems wrong. If the parent really does want to save state,
		    // it will be some other type besides an accessory, so when we try
		    // to cast the result as an accessory, it will not be the right
		    // class. Should return null here.
            Log.d(TAG, timestamp() + "Calling super.onRetainNonConfigurationInstance");
            Object val = super.onRetainNonConfigurationInstance();
            if (val != null) {
                Log.d(TAG, timestamp() + "Got non-null result");
            } else {
                Log.d(TAG, timestamp() + "Got null result");
            }
            return val;
            //return super.onRetainNonConfigurationInstance();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
        Log.d(TAG, timestamp() + "In onResume");

		if (mAccessory != null) {
            Log.d(TAG, timestamp() + "Found non-null mAccessory");
            openAccessory(mAccessory);
			return;
		}

		UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        if (accessories != null) {
            Log.d(TAG, timestamp() + "getAccessoryList returned non-null");
        } else {
            Log.d(TAG, timestamp() + "getAccessoryList returned null");
        }
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
            Log.d(TAG, timestamp() + "Got non-null accessory");
			if (mUsbManager.hasPermission(accessory)) {
                Log.d(TAG, timestamp() + "hasPermission is true");
				openAccessory(accessory);
			} else {
                Log.d(TAG, timestamp() + "Checking for permission request");
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
                        Log.d(TAG, timestamp() + "Sending permission request");
						mUsbManager.requestPermission(accessory,
								mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		} else {
			Log.d(TAG, timestamp() + "mAccessory is null");
		}
	}

	@Override
	public void onPause() {
		super.onPause();
        Log.d(TAG, timestamp() + "In onPause");
		closeAccessory();
	}

	@Override
	public void onDestroy() {
        Log.d(TAG, timestamp() + "In onDestroy");
		unregisterReceiver(mUsbReceiver);
		super.onDestroy();
	}

	private void openAccessory(UsbAccessory accessory) {
        Log.d(TAG, timestamp() + "In openAccessory");
        if (accessory == null) {
            Log.d(TAG, timestamp() + "Received null accessory");
            return;
        }
        mAccessory = accessory;
        Log.d(TAG, timestamp() + "About to start worker thread");
	    Thread thread = new Thread(null, this, "DemoKitWorker");
		thread.start();
		Log.d(TAG, timestamp() + "Worker started");
	}

	private void closeAccessory() {
        Log.d(TAG, timestamp() + "In closeAccessory");
		enableControls(false);
        workerQuit.set(true);
	}

    protected void enableControls(boolean enable) {
        if (enable) {
            // Show the controls
            setContentView(R.layout.main);
        } else {
            // Hide the controls
            setContentView(R.layout.no_device);
        }
    }
    
    // Worker thread operations
    
	private int composeInt(byte hi, byte lo) {
		int val = (int) hi & 0xff;
		val *= 256;
		val += (int) lo & 0xff;
		return val;
	}
	
	public static String timestamp() {
	    long time = SystemClock.elapsedRealtime();
	    long millis = time % 1000;
	    time = time / 1000;
	    long secs = time % 60;
	    time = time / 60;
	    long mins = time % 60;
	    time = time / 60;
	    return String.format("%d:%d:%d.%d: ", time, mins, secs, millis);
	}

    /** 
     * Read from USB off the UI thread.  Our messages are CRLF-terminated
     * 8-bit text.  We read into a fixed size buffer and assemble messages
     * until a CRLF is reached (or rather, for simplicity, we strip CR and
     * read until we get an LF).
     * 
     * Normal operation seems to be to let run() exit when there's no data.
     * That means (presumably) it will exit between messages from the board.
     * When it exits, the thread dies.  Thus a new thread is started for each
     * message, which is inefficient.  This version allows that to happen.
     * 
     * @ToDo: Would be better to wait between messages.  Any way we can get
     * waked when new data is ready, instead of polling?
     */
    public void run() {
        Log.d(TAG, timestamp() + "Worker thread started.");
        
        // Return value from USB available and read.
        int ret = 0;
        // Buffer is longer than any expected packet, but we're not yet sure
        // that packets might not be split across several reads.  This initial
        // version has no recourse if that happens -- it does not have a way to
        // wait for more data to arrive later.  Thus we treat each read as a
        // separate packet, regardless of whether it's properly terminated.
        byte[] buffer = new byte[BUFFER_LEN];
        // Index into buffer.
        int i;
        // Flag for end of message seen.
        boolean msgDone = false;
        // Timestamp -- we'll use elapsed time since boot, not wall clock, as
        // the latter can get changed arbitrarily, and we want accurate
        // intervals.
        long time;
        // This is a stand-in for a data class.  Here we are just going to
        // display and log the messages.  We'll assemble the message here.
        StringBuilder msgText = new StringBuilder(MAX_MESSAGE_LEN);
        
        // Should not get here without mAcessory set up.
        if (mAccessory == null) {
            Log.d(TAG, timestamp() + "Worker started without accessory");
            return;
        }
        
        // Get our file streams in the worker, not on the UI thread.
        // If the file streams are already set up, there's already a worker,
        // in which case, don't start another.
        synchronized (guardWorker) {
            if (mFileDescriptor != null) {
                Log.d(TAG, timestamp() + "A worker is already started");
                return;
            }
            mFileDescriptor = mUsbManager.openAccessory(mAccessory);
        }
        if (mFileDescriptor == null) {
            Log.d(TAG, timestamp() + "UsbManager openAccessory failed");
            return;
        }
        Log.d(TAG, timestamp() + "UsbManager openAccessory returned non-null");
        FileDescriptor fd = mFileDescriptor.getFileDescriptor();
        mInputStream = new FileInputStream(fd);
        mOutputStream = new FileOutputStream(fd);
        
        // Have the UI show the controls.
        sendEnableControls(true);
        
        // Check for quit signal, else loop over reads.
        while (!workerQuit.get()) {

            // Wait until there is data ready before doing the read call, which
            // will block if there's no data.  Doing this because read does not
            // allow setting a timeout, and it's not clear that a blocked read
            // will ever succeed if the device on the other end is temporarily
            // hung, then continues.
            try {
                while (true) {
                    Log.d(TAG, timestamp() + "About to call available()");
                    ret = mInputStream.available();
                    Log.d(TAG, timestamp() + "available() returned " + ret);
                    if (ret > 0) break;
                    try {
                        Thread.sleep(INPUT_RETRY_WAIT);
                    } catch (InterruptedException e) {
                    }
                }
            } catch (IOException e1) {
                Log.d(TAG, timestamp() + "available() threw IOException, returning from run()", e1);
                return;
            }

            try {
                // Returns # bytes read or -1 if no more data.  Note -1 is not
                // a temporary condition -- it's "end of file".  This
                // FileInputStream object won't start delivering data again
                // after end of file even if another USB message shows up --
                // that would need a fresh FileInputStream.  So we can't just
                // loop back and do another read again after we see a -1.
                Log.d(TAG, timestamp() + "About to call read()");
                ret = mInputStream.read(buffer);
                Log.d(TAG, timestamp() + "read() returned " + ret);
            } catch (IOException e) {
                Log.d(TAG, timestamp() + "read() threw IOException, returning from run()", e);
                closeIO();
                return;
            }
            if (ret < 0) {
                Log.d(TAG, timestamp() + "read() returned end of file, returning from run()");
                closeIO();
                return;
            }

            // Loop over all the data we just got, which may contain multiple
            // messages.
            i = 0;
            while (i < ret) {
                // Check frequently for the user's request to send to the board.
                serviceSendRequested();

                // For now, we believe we'll receive entire messages in one read,
                // so here, we're at the start of a new message.
                msgDone = false;
                // Empty our message storage.
                msgText.setLength(0);
                // Get the time and put it in the message.  Don't bother with
                // formatting.
                time = SystemClock.elapsedRealtime();
                msgText.append(time).append((char)COMMA);

                // Read until we see a CRLF, which ends one message, or we run out
                // of data.
                while (i < ret && !msgDone) {
                    int len = ret - i;
                    byte b = buffer[i];
                    switch (b) {
                    case CR:
                        if (len <= 2 || buffer[i+1] == LF) {
                            // At end of message -- send it to our handler.
                            // This is Linux -- don't need the CR.
                            msgText.append((char)LF);
                            updateLogFromWorker(new String(msgText));
                            // In case there is more in the buffer, skip the CRLF.
                            i += 2;
                            msgDone = true;
                        } else {
                            // CR in the middle of a message -- probably a mistake.
                            msgText.append((char)CR);
                            ++i;
                        }
                        break;

                    case AMP:
                        // &I prefixes a two-byte analog value, high byte first.
                        if (len >= 4 && ((buffer[i+1] == UPPER_I) || (buffer[i+1] == LOWER_I))) {
                            int val = composeInt(buffer[i+2], buffer[i+3]);
                            msgText.append(val);
                            i += 4;
                        } else {
                            // It's just an &...
                            msgText.append((char)AMP);
                            i++;
                        }
                        break;

                    default:
                        msgText.append((char)buffer[i]);
                        ++i;
                        break;
                    }
                }
            }
        }
        
        // We've been asked to quit.
        closeIO();
    }
    
    protected void closeIO() {
        if (mInputStream != null) {
            try {
                mInputStream.close();
            } catch (IOException e) { }
            mInputStream = null;
        }
        if (mOutputStream != null) {
            try {
                mOutputStream.close();
            } catch (IOException e) { }
            mOutputStream = null;
        }
        // Note could use ParcelFileDescriptor.AutoCloseInputStream and
        // ParcelFileDescriptor.AutoCloseOutputStream to avoid this close.
        if (mFileDescriptor != null) {
            try {
                mFileDescriptor.close();
            } catch (IOException e) { }
            mFileDescriptor = null;
        }
        
        Log.d(TAG, timestamp() + "Worker quitting");
    }
    
	public void sendBytes(byte[] msg) {
	    if (mOutputStream != null) {
	        try {
	            Log.d(TAG, timestamp() + "About to write");
	            mOutputStream.write(msg);
	        } catch (IOException e) {
	            Log.e(TAG, timestamp() + "write failed", e);
	        }
	    }
	}
	
	public void sendCommand(byte command, byte target, int value) {
	    if (target != -1) {
	        byte[] buffer = new byte[3];
	        if (value > 255)
	            value = 255;

	        buffer[0] = command;
	        buffer[1] = target;
	        buffer[2] = (byte) value;

	        sendBytes(buffer);
	    }
	}
	
	// Communication between worker and UI threads
	
	// Message types
	protected static final int MESSAGE_LOG = 1;
	protected static final int MESSAGE_CONTROLS = 2;
	
	/** Receives requests from the worker to write to UI */
    Handler mHandler = new Handler() {
	    @Override
	    public void handleMessage(Message msg) {
	        switch (msg.what) {
	        case MESSAGE_LOG:
	            updateLog((String)msg.obj);
	            // Note obj is a String, so can't reuse it. Allow it to be GCd.
	            msg.obj = null;
	            break;
	        case MESSAGE_CONTROLS:
	            enableControls((msg.arg1 > 0) ? true : false);
	            break;
	        }
	    }
	};
	
	/** Worker uses this to send requests to the UI */
	protected void updateLogFromWorker(String msgText) {
        Message m = Message.obtain(mHandler);
        m.obj = msgText;
        mHandler.sendMessage(m);
	}
	
	/** Worker uses this to ask the UI thread to show the user controls. */
	protected void sendEnableControls(boolean enable) {
	    Message m = Message.obtain(mHandler);
        m.arg1 = enable ? 1 : 0;
        mHandler.sendMessage(m);
	}
	
    /** This will be both a lock between UI and worker threads, and the flag
     *  for some condition, e.g. whether the user pressed the send button, or
     *  whether the worker thread is being asked to quit.  This is not intended
     *  to prevent a change after one thread has checked the value -- it's used
     *  when one side wants to raise a flag, and the other will service it and
     *  clear it.  Note we can't use a Boolean for this -- it's immutable. */
    protected class Flag {
        private boolean state;
        public Flag(boolean initialState) {
            state = initialState;
        }
        public synchronized void set(boolean newState) {
            state = newState;
        }
        public synchronized boolean get() {
            return state;
        }
    }
    
    protected Flag sendRequested = new Flag(false);
    protected static final String SEND_PREFIX = "Hello";
    // This is only set by the worker so doesn't need to be guarded.
    protected int sendCount = 0;
    
	/** Called by the worker to check and service the user's send request.
	 *  Note we don't hold the lock while actually sending -- we just hold it
	 *  while checking and setting the flag. */
	protected void serviceSendRequested() {
	    boolean wantsSend = false;
	    synchronized(sendRequested) {
	        wantsSend = sendRequested.get();
	        sendRequested.set(false);
	    }
	    if (wantsSend) {
	        ++sendCount;
	        byte[] msg = (SEND_PREFIX + ":" + String.valueOf(sendCount) + "\r\n").getBytes();
	        sendBytes(msg);
	    }
	}
	
	protected Flag workerQuit = new Flag(false);
	
	// This is actually not for UI - worker coordination, but to prevent
	// multiple workers from being started by onResume.
	private Object guardWorker = new Object();
	
    // UI handling
    
    /** True if user has paused scrolling. Normally, we scroll the text region
     *  up as each new message is added to the end. */
    private boolean scrollPaused = false;
    
    /** Add a message to the log and display. Messages should end with a newline
     *  or crlf. */
    private void updateLog(String msg) {
        TextView logView = (TextView)findViewById(R.id.log);
        String stampedMsg = timestamp() + msg;
        logView.append(stampedMsg);
        if (!scrollPaused) {
            ScrollView scrollView = (ScrollView)findViewById(R.id.scroll);
            scrollView.fullScroll(ScrollView.FOCUS_DOWN);
        }
        Log.i(TAG, stampedMsg);
    }
    
    /** Set a flag for our worker thread that the user wants a message sent
     *  to the Arduino. */
    public void sendClick(View view) {
        sendRequested.set(true);
    }
    
    /** Clear the log view. */
    public void clearClick(View view) {
        TextView logView = (TextView)findViewById(R.id.log);
        logView.setText("");
    }
    
    /** Handle a pause or unpause request -- stop or restart scrolling.
     *  (Received messages will continue to be added to the text view.) */
    public void pauseClick(View view) {
        if (scrollPaused) {
            scrollPaused = false;
            ScrollView scrollView = (ScrollView)findViewById(R.id.scroll);
            scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            ((Button)view).setText(getString(R.string.pause));
            
        } else {
            scrollPaused = true;
            ((Button)view).setText(getString(R.string.unpause));
        }
    }
}
