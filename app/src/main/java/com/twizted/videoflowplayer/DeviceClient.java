package com.twizted.videoflowplayer;
/*
 * ALL DISTRIBUTIONS OF THIS SOFTWARE SHALL REPRODUCE THIS NOTICE IN FULL.
 *
 * NOTICE
 *
 * Copyright (c) 2021 Amino Communications Ltd. ("Amino")
 *
 * All rights in inventions, patent rights, copyright, rights in designs,
 * rights in trade marks and all other intellectual property rights
 * whatsoever subsisting in or in relation to this software and/or its
 * accompanying documentation are and shall remain the property of Amino
 * and/or its licensors, and nothing herein shall transfer to you any
 * right, title, interest or license in or to any such intellectual
 * property rights.  All rights not expressly granted are reserved to
 * Amino and/or its licensors.
 *
 * TO THE EXTENT THAT THIS SOFTWARE INCLUDES SOFTWARE WHICH IS SUBJECT
 * TO THE TERMS OF ANY OPEN SOURCE LICENSE, THE TERMS OF SUCH LICENSES
 * AND (3) BELOW SHALL APPLY TO THE COMPONENTS CONCERNED.
 *
 * UNLESS WE HAVE EXPRESSLY AGREED OTHERWISE IN WRITING SIGNED BY AN
 * AUTHORIZED REPRESENTATIVE OF AMINO:
 *
 * 1. NO LICENSE EXPRESS OR IMPLIED IS GRANTED WHATSOVER.
 *
 * 2. THIS SOFTWARE MAY NOT BE MODIFIED, REPRODUCED, DISTRIBUTED
 * OR PUBLISHED EITHER IN WHOLE OR IN PART;
 *
 * 3. THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * AMINO OR ITS LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ALL DISTRIBUTIONS OF THIS SOFTWARE SHALL REPRODUCE THIS NOTICE IN FULL.
 */


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.aminocom.device.IDeviceRemoteService;

public class DeviceClient {
    private static final String TAG = DeviceClient.class.getSimpleName();

    private static final String DEVICE_REMOTE_SERVICE_PACKAGE = "com.aminocom.device";
    private static final String DEVICE_REMOTE_SERVICE_CLASS = DEVICE_REMOTE_SERVICE_PACKAGE + ".DeviceRemoteService";

    private static IDeviceRemoteService mService;
    private static DeviceRemoteServiceConnection mConnection;

    private Context mContext;

    public DeviceClient(Context context, DeviceRemoteServiceConnection connection) {
        mContext = context;
        mConnection = connection;
    }

    // ----------------------------------------------------------------------
    // Code showing how to deal with remote service.
    // ----------------------------------------------------------------------

    public static class DeviceRemoteServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "onServiceConnected(): className=" + className);
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = IDeviceRemoteService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "onServiceDisconnected(): className=" + className);
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
        }
    }

    public static IDeviceRemoteService getService() {
        return mService;
    }

    public static int getVersion() {
        return IDeviceRemoteService.VERSION;
    }


    public void connect() throws Exception {
        // Bind remote service on setUp()
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(DEVICE_REMOTE_SERVICE_PACKAGE, DEVICE_REMOTE_SERVICE_CLASS));
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public void disconnect() throws Exception {
        // Unbind remote service on tearDown()
        mContext.unbindService(mConnection);
    }

    public String getDeviceParameter(final String key, final String def) throws Exception {
        return mService.getDeviceParameter(key, def);
    }

    public String getApplicationParameter(final String key, final String def) throws Exception {
        return mService.getApplicationParameter(key, def);
    }

    public boolean setApplicationParameter(final String key, final String value) throws Exception {
        return mService.setApplicationParameter(key, value);
    }

    public byte[] getEthernetMacAddress() throws Exception {
        return mService.getEthernetMacAddress();
    }

    public String getDeviceSerialNumber() throws Exception {
        return mService.getDeviceSerialNumber();
    }

    public String getDeviceHardwareModel() throws Exception {
        return mService.getDeviceHardwareModel();
    }
}
