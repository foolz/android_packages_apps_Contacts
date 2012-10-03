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
package com.android.contacts.list;

import com.google.android.collect.Lists;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.os.Bundle;
import android.provider.ContactsContract.Profile;

import java.util.List;

/**
 * A loader for use in the default contact list, which will also query for the user's profile
 * if configured to do so.
 */
public class ProfileAndContactsLoader extends CursorLoader {

    private boolean mLoadProfile;
    private String[] mProjection;

    public ProfileAndContactsLoader(Context context) {
        super(context);
    }

    public void setLoadProfile(boolean flag) {
        mLoadProfile = flag;
    }

    public void setProjection(String[] projection) {
        super.setProjection(projection);
        mProjection = projection;
    }

    @Override
    public Cursor loadInBackground() {
        // First load the profile, if enabled.
        List<Cursor> cursors = Lists.newArrayList();
        if (mLoadProfile) {
            cursors.add(loadProfile());
        }
        final Cursor contactsCursor = super.loadInBackground();
        cursors.add(contactsCursor);
        return new MergeCursor(cursors.toArray(new Cursor[cursors.size()])) {
            @Override
            public Bundle getExtras() {
                // Need to get the extras from the contacts cursor.
                return contactsCursor.getExtras();
            }
        };
    }

    /**
     * Loads the profile into a MatrixCursor.
     */
    private MatrixCursor loadProfile() {
        Cursor cursor = getContext().getContentResolver().query(Profile.CONTENT_URI, mProjection,
                null, null, null);
        try {
            MatrixCursor matrix = new MatrixCursor(mProjection);
            Object[] row = new Object[mProjection.length];
            while (cursor.moveToNext()) {
                for (int i = 0; i < row.length; i++) {
                    row[i] = cursor.getString(i);
                }
                matrix.addRow(row);
            }
            return matrix;
        } finally {
            cursor.close();
        }
    }
}
