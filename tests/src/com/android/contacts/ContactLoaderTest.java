/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts;

import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountWithDataSet;
import com.android.contacts.model.BaseAccountType;
import com.android.contacts.test.InjectedServices;
import com.android.contacts.tests.mocks.ContactsMockContext;
import com.android.contacts.tests.mocks.MockAccountTypeManager;
import com.android.contacts.tests.mocks.MockContentProvider;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.StatusUpdates;
import android.test.LoaderTestCase;
import android.test.suitebuilder.annotation.LargeTest;

/**
 * Runs ContactLoader tests for the the contact-detail and editor view.
 */
@LargeTest
public class ContactLoaderTest extends LoaderTestCase {
    private ContactsMockContext mMockContext;
    private MockContentProvider mContactsProvider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockContext = new ContactsMockContext(getContext());
        mContactsProvider = mMockContext.getContactsProvider();

        InjectedServices services = new InjectedServices();
        AccountType accountType = new BaseAccountType() {
            @Override
            public boolean areContactsWritable() {
                return false;
            }
        };
        accountType.accountType = "mockAccountType";

        AccountWithDataSet account =
                new AccountWithDataSet("mockAccountName", "mockAccountType", null);

        mMockContext.setMockAccountTypeManager(
                new MockAccountTypeManager(
                        new AccountType[] { accountType }, new AccountWithDataSet[] { account }));
    }

    @Override
    protected void tearDown() throws Exception {
        mMockContext = null;
        mContactsProvider = null;
        super.tearDown();
    }

    private ContactLoader.Result assertLoadContact(Uri uri) {
        final ContactLoader loader = new ContactLoader(mMockContext, uri, true);
        return getLoaderResultSynchronously(loader);
    }

    public void testNullUri() {
        ContactLoader.Result result = assertLoadContact(null);
        assertTrue(result.isError());
    }

    public void testEmptyUri() {
        ContactLoader.Result result = assertLoadContact(Uri.EMPTY);
        assertTrue(result.isError());
    }

    public void testInvalidUri() {
        ContactLoader.Result result = assertLoadContact(Uri.parse("content://wtf"));
        assertTrue(result.isError());
    }

    public void testLoadContactWithContactIdUri() {
        // Use content Uris that only contain the ID
        final long contactId = 1;
        final long rawContactId = 11;
        final long dataId = 21;

        final String lookupKey = "aa%12%@!";
        final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        final Uri entityUri = Uri.withAppendedPath(baseUri, Contacts.Entity.CONTENT_DIRECTORY);
        final Uri lookupUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey),
                contactId);

        ContactQueries queries = new ContactQueries();
        mContactsProvider.expectTypeQuery(baseUri, Contacts.CONTENT_ITEM_TYPE);
        queries.fetchAllData(entityUri, contactId, rawContactId, dataId, lookupKey);

        ContactLoader.Result contact = assertLoadContact(baseUri);

        assertEquals(contactId, contact.getId());
        assertEquals(rawContactId, contact.getNameRawContactId());
        assertEquals(DisplayNameSources.STRUCTURED_NAME, contact.getDisplayNameSource());
        assertEquals(lookupKey, contact.getLookupKey());
        assertEquals(lookupUri, contact.getLookupUri());
        assertEquals(1, contact.getEntities().size());
        assertEquals(1, contact.getStatuses().size());
        mContactsProvider.verify();
    }

    public void testLoadContactWithOldStyleUri() {
        // Use content Uris that only contain the ID but use the format used in Donut
        final long contactId = 1;
        final long rawContactId = 11;
        final long dataId = 21;

        final String lookupKey = "aa%12%@!";
        final Uri legacyUri = ContentUris.withAppendedId(
                Uri.parse("content://contacts"), rawContactId);
        final Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
        final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        final Uri lookupUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey),
                contactId);
        final Uri entityUri = Uri.withAppendedPath(lookupUri, Contacts.Entity.CONTENT_DIRECTORY);

        ContactQueries queries = new ContactQueries();
        queries.fetchContactIdAndLookupFromRawContactUri(rawContactUri, contactId, lookupKey);
        queries.fetchAllData(entityUri, contactId, rawContactId, dataId, lookupKey);

        ContactLoader.Result contact = assertLoadContact(legacyUri);

        assertEquals(contactId, contact.getId());
        assertEquals(rawContactId, contact.getNameRawContactId());
        assertEquals(DisplayNameSources.STRUCTURED_NAME, contact.getDisplayNameSource());
        assertEquals(lookupKey, contact.getLookupKey());
        assertEquals(lookupUri, contact.getLookupUri());
        assertEquals(1, contact.getEntities().size());
        assertEquals(1, contact.getStatuses().size());
        mContactsProvider.verify();
    }

    public void testLoadContactWithRawContactIdUri() {
        // Use content Uris that only contain the ID but use the format used in Donut
        final long contactId = 1;
        final long rawContactId = 11;
        final long dataId = 21;

        final String lookupKey = "aa%12%@!";
        final Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
        final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        final Uri lookupUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey),
                contactId);
        final Uri entityUri = Uri.withAppendedPath(lookupUri, Contacts.Entity.CONTENT_DIRECTORY);

        ContactQueries queries = new ContactQueries();
        mContactsProvider.expectTypeQuery(rawContactUri, RawContacts.CONTENT_ITEM_TYPE);
        queries.fetchContactIdAndLookupFromRawContactUri(rawContactUri, contactId, lookupKey);
        queries.fetchAllData(entityUri, contactId, rawContactId, dataId, lookupKey);

        ContactLoader.Result contact = assertLoadContact(rawContactUri);

        assertEquals(contactId, contact.getId());
        assertEquals(rawContactId, contact.getNameRawContactId());
        assertEquals(DisplayNameSources.STRUCTURED_NAME, contact.getDisplayNameSource());
        assertEquals(lookupKey, contact.getLookupKey());
        assertEquals(lookupUri, contact.getLookupUri());
        assertEquals(1, contact.getEntities().size());
        assertEquals(1, contact.getStatuses().size());
        mContactsProvider.verify();
    }

    public void testLoadContactWithContactLookupUri() {
        // Use lookup-style Uris that do not contain the Contact-ID

        final long contactId = 1;
        final long rawContactId = 11;
        final long dataId = 21;

        final String lookupKey = "aa%12%@!";
        final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        final Uri lookupNoIdUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
        final Uri lookupUri = ContentUris.withAppendedId(lookupNoIdUri, contactId);
        final Uri entityUri = Uri.withAppendedPath(lookupNoIdUri, Contacts.Entity.CONTENT_DIRECTORY);

        ContactQueries queries = new ContactQueries();
        mContactsProvider.expectTypeQuery(lookupNoIdUri, Contacts.CONTENT_ITEM_TYPE);
        queries.fetchAllData(entityUri, contactId, rawContactId, dataId, lookupKey);

        ContactLoader.Result contact = assertLoadContact(lookupNoIdUri);

        assertEquals(contactId, contact.getId());
        assertEquals(rawContactId, contact.getNameRawContactId());
        assertEquals(DisplayNameSources.STRUCTURED_NAME, contact.getDisplayNameSource());
        assertEquals(lookupKey, contact.getLookupKey());
        assertEquals(lookupUri, contact.getLookupUri());
        assertEquals(1, contact.getEntities().size());
        assertEquals(1, contact.getStatuses().size());
        mContactsProvider.verify();
    }

    public void testLoadContactWithContactLookupAndIdUri() {
        // Use lookup-style Uris that also contain the Contact-ID
        final long contactId = 1;
        final long rawContactId = 11;
        final long dataId = 21;

        final String lookupKey = "aa%12%@!";
        final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        final Uri lookupUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey),
                contactId);
        final Uri entityUri = Uri.withAppendedPath(lookupUri, Contacts.Entity.CONTENT_DIRECTORY);

        ContactQueries queries = new ContactQueries();
        mContactsProvider.expectTypeQuery(lookupUri, Contacts.CONTENT_ITEM_TYPE);
        queries.fetchAllData(entityUri, contactId, rawContactId, dataId, lookupKey);

        ContactLoader.Result contact = assertLoadContact(lookupUri);

        assertEquals(contactId, contact.getId());
        assertEquals(rawContactId, contact.getNameRawContactId());
        assertEquals(DisplayNameSources.STRUCTURED_NAME, contact.getDisplayNameSource());
        assertEquals(lookupKey, contact.getLookupKey());
        assertEquals(lookupUri, contact.getLookupUri());
        assertEquals(1, contact.getEntities().size());
        assertEquals(1, contact.getStatuses().size());
        mContactsProvider.verify();
    }

    public void testLoadContactWithContactLookupWithIncorrectIdUri() {
        // Use lookup-style Uris that contain incorrect Contact-ID
        // (we want to ensure that still the correct contact is chosen)

        final long contactId = 1;
        final long wrongContactId = 2;
        final long rawContactId = 11;
        final long wrongRawContactId = 12;
        final long dataId = 21;

        final String lookupKey = "aa%12%@!";
        final String wrongLookupKey = "ab%12%@!";
        final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        final Uri wrongBaseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, wrongContactId);
        final Uri lookupUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey),
                contactId);
        final Uri lookupWithWrongIdUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey),
                wrongContactId);
        final Uri entityUri = Uri.withAppendedPath(lookupWithWrongIdUri,
                Contacts.Entity.CONTENT_DIRECTORY);

        ContactQueries queries = new ContactQueries();
        mContactsProvider.expectTypeQuery(lookupWithWrongIdUri, Contacts.CONTENT_ITEM_TYPE);
        queries.fetchAllData(entityUri, contactId, rawContactId, dataId, lookupKey);

        ContactLoader.Result contact = assertLoadContact(lookupWithWrongIdUri);

        assertEquals(contactId, contact.getId());
        assertEquals(rawContactId, contact.getNameRawContactId());
        assertEquals(DisplayNameSources.STRUCTURED_NAME, contact.getDisplayNameSource());
        assertEquals(lookupKey, contact.getLookupKey());
        assertEquals(lookupUri, contact.getLookupUri());
        assertEquals(1, contact.getEntities().size());
        assertEquals(1, contact.getStatuses().size());

        mContactsProvider.verify();
    }

    class ContactQueries {
        public void fetchAllData(
                Uri baseUri, long contactId, long rawContactId, long dataId, String encodedLookup) {
            mContactsProvider.expectQuery(baseUri)
                    .withProjection(new String[] {
                        Contacts.NAME_RAW_CONTACT_ID, Contacts.DISPLAY_NAME_SOURCE,
                        Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME,
                        Contacts.DISPLAY_NAME_ALTERNATIVE, Contacts.PHONETIC_NAME,
                        Contacts.PHOTO_ID, Contacts.STARRED, Contacts.CONTACT_PRESENCE,
                        Contacts.CONTACT_STATUS, Contacts.CONTACT_STATUS_TIMESTAMP,
                        Contacts.CONTACT_STATUS_RES_PACKAGE, Contacts.CONTACT_STATUS_LABEL,

                        Contacts.Entity.CONTACT_ID,
                        Contacts.Entity.RAW_CONTACT_ID,

                        RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE,
                        RawContacts.DATA_SET, RawContacts.ACCOUNT_TYPE_AND_DATA_SET,
                        RawContacts.DIRTY, RawContacts.VERSION, RawContacts.SOURCE_ID,
                        RawContacts.SYNC1, RawContacts.SYNC2, RawContacts.SYNC3, RawContacts.SYNC4,
                        RawContacts.DELETED, RawContacts.NAME_VERIFIED,

                        Contacts.Entity.DATA_ID,

                        Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5,
                        Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10,
                        Data.DATA11, Data.DATA12, Data.DATA13, Data.DATA14, Data.DATA15,
                        Data.SYNC1, Data.SYNC2, Data.SYNC3, Data.SYNC4,
                        Data.DATA_VERSION, Data.IS_PRIMARY,
                        Data.IS_SUPER_PRIMARY, Data.MIMETYPE, Data.RES_PACKAGE,

                        GroupMembership.GROUP_SOURCE_ID,

                        Data.PRESENCE, Data.CHAT_CAPABILITY,
                        Data.STATUS, Data.STATUS_RES_PACKAGE, Data.STATUS_ICON,
                        Data.STATUS_LABEL, Data.STATUS_TIMESTAMP,

                        Contacts.PHOTO_URI,

                        Contacts.SEND_TO_VOICEMAIL,
                        Contacts.CUSTOM_RINGTONE,
                        Contacts.IS_USER_PROFILE,
                    })
                    .withSortOrder(Contacts.Entity.RAW_CONTACT_ID)
                    .returnRow(
                        rawContactId, 40,
                        "aa%12%@!", "John Doe", "Doe, John", "jdo",
                        0, 0, StatusUpdates.AVAILABLE,
                        "Having lunch", 0,
                        "mockPkg1", 10,

                        contactId,
                        rawContactId,

                        "mockAccountName", "mockAccountType", null, "mockAccountType",
                        0, 1, 0,
                        "sync1", "sync2", "sync3", "sync4",
                        0, 0,

                        dataId,

                        "dat1", "dat2", "dat3", "dat4", "dat5",
                        "dat6", "dat7", "dat8", "dat9", "dat10",
                        "dat11", "dat12", "dat13", "dat14", "dat15",
                        "syn1", "syn2", "syn3", "syn4",

                        0, 0,
                        0, StructuredName.CONTENT_ITEM_TYPE, "mockPkg2",

                        "groupId",

                        StatusUpdates.INVISIBLE, null,
                        "Having dinner", "mockPkg3", 0,
                        20, 0,

                        "content:some.photo.uri",

                        0,
                        null,
                        0
                    );
        }

        void fetchLookupAndId(final Uri sourceUri, final long expectedContactId,
                final String expectedEncodedLookup) {
            mContactsProvider.expectQuery(sourceUri)
                    .withProjection(Contacts.LOOKUP_KEY, Contacts._ID)
                    .returnRow(expectedEncodedLookup, expectedContactId);
        }

        void fetchContactIdAndLookupFromRawContactUri(final Uri rawContactUri,
                final long expectedContactId, final String expectedEncodedLookup) {
            // TODO: use a lighter query by joining rawcontacts with contacts in provider
            // (See ContactContracts.java)
            final Uri dataUri = Uri.withAppendedPath(rawContactUri,
                    RawContacts.Data.CONTENT_DIRECTORY);
            mContactsProvider.expectQuery(dataUri)
                    .withProjection(RawContacts.CONTACT_ID, Contacts.LOOKUP_KEY)
                    .returnRow(expectedContactId, expectedEncodedLookup);
        }
    }
}
