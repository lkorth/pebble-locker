package com.lukekorth.pebblelocker.test;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;

import com.lukekorth.pebblelocker.Locker;
import com.lukekorth.pebblelocker.helpers.DeviceHelper;
import com.lukekorth.pebblelocker.helpers.PebbleHelper;
import com.lukekorth.pebblelocker.helpers.WifiHelper;
import com.lukekorth.pebblelocker.receivers.BaseBroadcastReceiver;
import com.lukekorth.pebblelocker.helpers.CustomDeviceAdminReceiver;

import org.mockito.Mock;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LockerTest extends BaseApplicationTestCase {

    @Mock DeviceHelper mDeviceHelper;
    @Mock WifiHelper mWifiHelper;
    @Mock PebbleHelper mPebbleHelper;
    @Mock DevicePolicyManager mDPM;

    private Locker mLocker;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mLocker = new Locker(getContext(), "TEST", mDeviceHelper, mWifiHelper, mPebbleHelper, mDPM);
    }

    public void testHandleLockingProxiesForceLockOption() {
        setEnabled();
        mPrefs.edit().putBoolean("key_force_lock", true).apply();

        mLocker.handleLocking(false);
        verify(mDPM, never()).lockNow();

        mLocker.handleLocking(true);
        verify(mDPM, times(1)).lockNow();
    }

    public void testHandleLockingUnlocksWhenConnectedAndLocked() {
        setEnabled();
        setConnected(true);
        when(mDeviceHelper.isLocked(true)).thenReturn(true);

        mLocker.handleLocking(false);

        verify(mDPM, times(1)).resetPassword("", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
    }

    public void testHandleLockingDoesNotExcessivelyUnlock() {
        setEnabled();
        setConnected(true);
        when(mDeviceHelper.isLocked(true)).thenReturn(false);

        mLocker.handleLocking(false);

        verify(mDPM, never()).resetPassword("", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
    }

    public void testHandleLockingLocksWhenNotConnectedAndNotLocked() {
        setEnabled();
        setConnected(false);
        when(mDeviceHelper.isLocked(false)).thenReturn(false);

        mLocker.handleLocking(false);

        verify(mDPM, times(1)).resetPassword("1234", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
    }

    public void testHandleLockingDoesNotExcessivelyLock() {
        setEnabled();
        setConnected(false);
        when(mDeviceHelper.isLocked(false)).thenReturn(true);

        mLocker.handleLocking(false);

        verify(mDPM, never()).resetPassword("1234", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
    }

    public void testLockReturnsEarlyIfNotEnabled() {
        mLocker.lock(true);

        verify(mDPM, never()).resetPassword("1234", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
    }

    public void testLockSetsPasswordCorrectlyAndSendsEvent() {
        setEnabled();

        mLocker.lock(true);

        verify(mDPM).resetPassword("1234", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
        assertTrue(mPrefs.getBoolean(BaseBroadcastReceiver.LOCKED, false));
        assertFalse(mPrefs.getBoolean(DeviceHelper.NEED_TO_UNLOCK_KEY, true));
        verify(mDeviceHelper).sendLockStatusChangedEvent();
    }

    public void testLockForceLocksScreenWhenForceLockIsTrue() {
        setEnabled();
        mPrefs.edit().putBoolean("key_force_lock", true).apply();

        mLocker.lock(true);

        verify(mDPM).lockNow();
    }

    public void testLockDoesNotForceLockScreenWhenForceLockIsFalse() {
        setEnabled();
        mPrefs.edit().putBoolean("key_force_lock", true).apply();

        mLocker.lock(false);

        verify(mDPM, never()).lockNow();
    }

    public void testLockOnlyForceLocksScreenWhenPreferenceIsTrue() {
        setEnabled();

        mPrefs.edit().putBoolean("key_force_lock", false).apply();
        mLocker.lock(true);
        verify(mDPM, never()).lockNow();

        mPrefs.edit().putBoolean("key_force_lock", true).apply();
        mLocker.lock(true);
        verify(mDPM, times(1)).lockNow();
    }

    public void testUnlockReturnsEarlyIfNotEnabled() {
        mLocker.unlock();

        verify(mDPM, never()).resetPassword("", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
    }

    public void testUnlocksWhenScreenIsOnAndOnLockScreen() {
        setEnabled();

        when(mDeviceHelper.isOnLockscreen()).thenReturn(true);
        when(mDeviceHelper.isScreenOn()).thenReturn(true);

        mLocker.unlock();

        verify(mDPM, times(1)).resetPassword("", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
        verify(mDeviceHelper, times(1)).sendLockStatusChangedEvent();
    }

    public void testUnlockRequiresPasswordOnceOnReconnectIfOptionIsEnabled() {
        setEnabled();
        mPrefs.edit().putBoolean("key_require_password_on_reconnect", true).apply();
        mPrefs.edit().putBoolean(DeviceHelper.NEED_TO_UNLOCK_KEY, false).apply();

        mLocker.unlock();

        verify(mDPM, never()).resetPassword("", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
        assertTrue(mPrefs.getBoolean(DeviceHelper.NEED_TO_UNLOCK_KEY, false));
    }

    public void testUnlockUnlocksAndSendsEvent() {
        setEnabled();

        mLocker.unlock();

        verify(mDPM, times(1)).resetPassword("", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
        assertFalse(mPrefs.getBoolean(DeviceHelper.NEED_TO_UNLOCK_KEY, true));
        verify(mDeviceHelper, times(1)).sendLockStatusChangedEvent();
    }

    public void testUnlockRestoresPasswordWhenUnlockFails() {
        setEnabled();
        when(mDPM.resetPassword("", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY)).thenThrow(new IllegalArgumentException());

        mLocker.unlock();

        verify(mDPM, times(1)).resetPassword("", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
        verify(mDPM, times(1)).resetPassword("1234", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
    }

    public void testUnlockTurnsOffScreenIfItWasTurnedOnDuringUnlock() {
        setEnabled();
        when(mDeviceHelper.isOnLockscreen()).thenReturn(true);
        when(mDeviceHelper.isScreenOn()).thenReturn(false).thenReturn(false).thenReturn(true);
        when(mDPM.resetPassword("", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY)).thenReturn(true);

        mLocker.unlock();

        verify(mDPM, times(1)).resetPassword("", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
        verify(mDPM, times(1)).lockNow();
    }

    public void testEnabledIsTrueWhenAllConditionsAreMet() {
        setEnabled();
        assertTrue(mLocker.enabled());
    }

    public void testEnabledIsFalseWhenAnyConditionIsNotMet() {
        when(mDPM.isAdminActive(new ComponentName(mContext, CustomDeviceAdminReceiver.class))).thenReturn(true);
        mPrefs.edit().putBoolean("key_enable_locker", true).apply();

        assertFalse(mLocker.enabled());
    }

    public void testConnectedToDeviceOrWifiIsTrueWhenAllDevicesAreConnected() {
        when(mWifiHelper.isTrustedWifiConnected()).thenReturn(true);
        createBluetoothDevice("test", "test", true, true);
        when(mPebbleHelper.isEnabledAndConnected()).thenReturn(true);

        assertTrue(mLocker.isConnectedToDeviceOrWifi());
    }

    public void testConnectedToDeviceOrWifiIsFalseWhenNoDevicesAreConnected() {
        when(mWifiHelper.isTrustedWifiConnected()).thenReturn(false);
        createBluetoothDevice("test", "test", false, true);
        when(mPebbleHelper.isEnabledAndConnected()).thenReturn(false);

        assertFalse(mLocker.isConnectedToDeviceOrWifi());
    }

    public void testConnectedToDeviceOrWifiIsTrueWhenOneDeviceIsConnected() {
        when(mWifiHelper.isTrustedWifiConnected()).thenReturn(false);
        createBluetoothDevice("test", "test", true, true);
        when(mPebbleHelper.isEnabledAndConnected()).thenReturn(false);

        assertTrue(mLocker.isConnectedToDeviceOrWifi());
    }

    /* helpers */
    private void setEnabled() {
        when(mDPM.isAdminActive(new ComponentName(mContext, CustomDeviceAdminReceiver.class))).thenReturn(true);
        mPrefs.edit().putBoolean("key_enable_locker", true).apply();
        mPrefs.edit().putString("key_password", "1234").apply();
    }

    private void setConnected(boolean connected) {
        when(mPebbleHelper.isEnabledAndConnected()).thenReturn(connected);
    }

}
