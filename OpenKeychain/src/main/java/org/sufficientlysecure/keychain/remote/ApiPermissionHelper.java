/*
 * Copyright (C) 2013-2015 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.remote;


import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Binder;

import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.util.OpenPgpApi;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.provider.KeychainContract;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;


/**
 * Abstract service class for remote APIs that handle app registration and user input.
 */
public class ApiPermissionHelper {

    private final Context mContext;
    private final ProviderHelper mProviderHelper;
    private PackageManager mPackageManager;

    public ApiPermissionHelper(Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mProviderHelper = new ProviderHelper(context);
    }

    public static class WrongPackageCertificateException extends Exception {
        private static final long serialVersionUID = -8294642703122196028L;

        public WrongPackageCertificateException(String message) {
            super(message);
        }
    }

    /**
     * Checks if caller is allowed to access the API
     *
     * @return null if caller is allowed, or a Bundle with a PendingIntent
     */
    protected Intent isAllowed(Intent data) {
        ApiPendingIntentFactory piFactory = new ApiPendingIntentFactory(mContext);

        try {
            if (isCallerAllowed()) {
                return null;
            } else {
                String packageName = getCurrentCallingPackage();
                Log.d(Constants.TAG, "isAllowed packageName: " + packageName);

                byte[] packageCertificate;
                try {
                    packageCertificate = getPackageCertificate(packageName);
                } catch (NameNotFoundException e) {
                    Log.e(Constants.TAG, "Should not happen, returning!", e);
                    // return error
                    Intent result = new Intent();
                    result.putExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR);
                    result.putExtra(OpenPgpApi.RESULT_ERROR,
                            new OpenPgpError(OpenPgpError.GENERIC_ERROR, e.getMessage()));
                    return result;
                }
                Log.e(Constants.TAG, "Not allowed to use service! return PendingIntent for registration!");

                PendingIntent pi = piFactory.createRegisterPendingIntent(data,
                        packageName, packageCertificate);

                // return PendingIntent to be executed by client
                Intent result = new Intent();
                result.putExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED);
                result.putExtra(OpenPgpApi.RESULT_INTENT, pi);

                return result;
            }
        } catch (WrongPackageCertificateException e) {
            Log.e(Constants.TAG, "wrong signature!", e);

            PendingIntent pi = piFactory.createErrorPendingIntent(data, mContext.getString(R.string.api_error_wrong_signature));

            // return PendingIntent to be executed by client
            Intent result = new Intent();
            result.putExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED);
            result.putExtra(OpenPgpApi.RESULT_INTENT, pi);

            return result;
        }
    }

    private byte[] getPackageCertificate(String packageName) throws NameNotFoundException {
        @SuppressLint("PackageManagerGetSignatures") // we do check the byte array of *all* signatures
        PackageInfo pkgInfo = mContext.getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
        // NOTE: Silly Android API naming: Signatures are actually certificates
        Signature[] certificates = pkgInfo.signatures;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (Signature cert : certificates) {
            try {
                outputStream.write(cert.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException("Should not happen! Writing ByteArrayOutputStream to concat certificates failed");
            }
        }

        // Even if an apk has several certificates, these certificates should never change
        // Google Play does not allow the introduction of new certificates into an existing apk
        // Also see this attack: http://stackoverflow.com/a/10567852
        return outputStream.toByteArray();
    }

    /**
     * Returns package name associated with the UID, which is assigned to the process that sent you the
     * current transaction that is being processed :)
     *
     * @return package name
     */
    protected String getCurrentCallingPackage() {
        String[] callingPackages = mPackageManager.getPackagesForUid(Binder.getCallingUid());

        // NOTE: No support for sharedUserIds
        // callingPackages contains more than one entry when sharedUserId has been used
        // No plans to support sharedUserIds due to many bugs connected to them:
        // http://java-hamster.blogspot.de/2010/05/androids-shareduserid.html
        String currentPkg = callingPackages[0];
        Log.d(Constants.TAG, "currentPkg: " + currentPkg);

        return currentPkg;
    }

    /**
     * Retrieves AccountSettings from database for the application calling this remote service
     */
    @Deprecated
    protected AccountSettings getAccSettings(String accountName) {
        String currentPkg = getCurrentCallingPackage();
        Log.d(Constants.TAG, "getAccSettings accountName: " + accountName);

        Uri uri = KeychainContract.ApiAccounts.buildByPackageAndAccountUri(currentPkg, accountName);

        return mProviderHelper.getApiAccountSettings(uri); // can be null!
    }

    @Deprecated
    protected Intent getCreateAccountIntent(Intent data, String accountName) {
        String packageName = getCurrentCallingPackage();
        Log.d(Constants.TAG, "getCreateAccountIntent accountName: " + accountName);

        ApiPendingIntentFactory piFactory = new ApiPendingIntentFactory(mContext);

        PendingIntent pi = piFactory.createAccountCreationPendingIntent(data,
                packageName, accountName);

        // return PendingIntent to be executed by client
        Intent result = new Intent();
        result.putExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED);
        result.putExtra(OpenPgpApi.RESULT_INTENT, pi);

        return result;
    }

    /**
     * Checks if process that binds to this service (i.e. the package name corresponding to the
     * process) is in the list of allowed package names.
     *
     * @return true if process is allowed to use this service
     * @throws WrongPackageCertificateException
     */
    private boolean isCallerAllowed() throws WrongPackageCertificateException {
        return isUidAllowed(Binder.getCallingUid());
    }

    private boolean isUidAllowed(int uid)
            throws WrongPackageCertificateException {

        String[] callingPackages = mPackageManager.getPackagesForUid(uid);

        // is calling package allowed to use this service?
        for (String currentPkg : callingPackages) {
            if (isPackageAllowed(currentPkg)) {
                return true;
            }
        }

        Log.e(Constants.TAG, "Uid is NOT allowed!");
        return false;
    }

    /**
     * Checks if packageName is a registered app for the API. Does not return true for own package!
     *
     * @throws WrongPackageCertificateException
     */
    private boolean isPackageAllowed(String packageName) throws WrongPackageCertificateException {
        Log.d(Constants.TAG, "isPackageAllowed packageName: " + packageName);

        ArrayList<String> allowedPkgs = mProviderHelper.getRegisteredApiApps();
        Log.d(Constants.TAG, "allowed: " + allowedPkgs);

        // check if package is allowed to use our service
        if (allowedPkgs.contains(packageName)) {
            Log.d(Constants.TAG, "Package is allowed! packageName: " + packageName);

            // check package signature
            byte[] currentCert;
            try {
                currentCert = getPackageCertificate(packageName);
            } catch (NameNotFoundException e) {
                throw new WrongPackageCertificateException(e.getMessage());
            }

            byte[] storedCert = mProviderHelper.getApiAppCertificate(packageName);
            if (Arrays.equals(currentCert, storedCert)) {
                Log.d(Constants.TAG,
                        "Package certificate is correct! (equals certificate from database)");
                return true;
            } else {
                throw new WrongPackageCertificateException(
                        "PACKAGE NOT ALLOWED! Certificate wrong! (Certificate not " +
                                "equals certificate from database)");
            }
        }

        Log.d(Constants.TAG, "Package is NOT allowed! packageName: " + packageName);
        return false;
    }

}
