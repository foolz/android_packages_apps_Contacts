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

package com.android.contacts.editor;

import com.android.contacts.R;
import com.android.contacts.datepicker.DatePicker;
import com.android.contacts.datepicker.DatePickerDialog;
import com.android.contacts.datepicker.DatePickerDialog.OnDateSetListener;
import com.android.contacts.model.AccountType.EditField;
import com.android.contacts.model.AccountType.EventEditType;
import com.android.contacts.model.DataKind;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.util.DateUtils;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Editor that allows editing Events using a {@link DatePickerDialog}
 */
public class EventFieldEditorView extends LabeledEditorView {
    /**
     * Exchange requires 8:00 for birthdays
     */
    private final static int DEFAULT_HOUR = 8;

    /**
     * Default string to show when there is no date selected yet.
     */
    private String mNoDateString;
    private int mPrimaryTextColor;
    private int mSecondaryTextColor;

    private Button mDateView;

    public EventFieldEditorView(Context context) {
        super(context);
    }

    public EventFieldEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EventFieldEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        Resources resources = mContext.getResources();
        mPrimaryTextColor = resources.getColor(R.color.primary_text_color);
        mSecondaryTextColor = resources.getColor(R.color.secondary_text_color);
        mNoDateString = mContext.getString(R.string.event_edit_field_hint_text);

        mDateView = (Button) findViewById(R.id.date_view);
        mDateView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog(R.id.dialog_event_date_picker);
            }
        });
    }

    @Override
    public void editNewlyAddedField() {
        showDialog(R.id.dialog_event_date_picker);
    }

    @Override
    protected void requestFocusForFirstEditField() {
        mDateView.requestFocus();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        mDateView.setEnabled(!isReadOnly() && enabled);
    }

    @Override
    public void setValues(DataKind kind, ValuesDelta entry, EntityDelta state, boolean readOnly,
            ViewIdGenerator vig) {
        if (kind.fieldList.size() != 1) throw new IllegalStateException("kind must have 1 field");
        super.setValues(kind, entry, state, readOnly, vig);

        mDateView.setEnabled(isEnabled() && !readOnly);

        rebuildDateView();
    }

    private void rebuildDateView() {
        final EditField editField = getKind().fieldList.get(0);
        final String column = editField.column;
        String data = DateUtils.formatDate(getContext(), getEntry().getAsString(column));
        if (TextUtils.isEmpty(data)) {
            mDateView.setText(mNoDateString);
            mDateView.setTextColor(mSecondaryTextColor);
            setDeleteButtonVisible(false);
        } else {
            mDateView.setText(data);
            mDateView.setTextColor(mPrimaryTextColor);
            setDeleteButtonVisible(true);
        }
    }

    @Override
    public boolean isEmpty() {
        final EditField editField = getKind().fieldList.get(0);
        final String column = editField.column;
        return TextUtils.isEmpty(getEntry().getAsString(column));
    }

    @Override
    public Dialog createDialog(Bundle bundle) {
        if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
        int dialogId = bundle.getInt(DIALOG_ID_KEY);
        switch (dialogId) {
            case R.id.dialog_event_date_picker:
                return createDatePickerDialog();
            default:
                return super.createDialog(bundle);
        }
    }

    @Override
    protected EventEditType getType() {
        return (EventEditType) super.getType();
    }

    @Override
    protected void onLabelRebuilt() {
        // if we changed to a type that requires a year, ensure that it is actually set
        final String column = getKind().fieldList.get(0).column;
        final String oldValue = getEntry().getAsString(column);
        final DataKind kind = getKind();

        final Calendar calendar = Calendar.getInstance(DateUtils.UTC_TIMEZONE, Locale.US);
        final int defaultYear = calendar.get(Calendar.YEAR);

        // Check whether the year is optional
        final boolean isYearOptional = getType().isYearOptional();

        if (!isYearOptional && !TextUtils.isEmpty(oldValue)) {
            final ParsePosition position = new ParsePosition(0);
            final Date date2 = kind.dateFormatWithoutYear.parse(oldValue, position);

            // Don't understand the date, lets not change it
            if (date2 == null) return;

            // This value is missing the year. Add it now
            calendar.setTime(date2);
            calendar.set(defaultYear, calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH), DEFAULT_HOUR, 0, 0);

            onFieldChanged(column, kind.dateFormatWithYear.format(calendar.getTime()));
            rebuildDateView();
        }
    }

    /**
     * Prepare dialog for entering a date
     */
    private Dialog createDatePickerDialog() {
        final String column = getKind().fieldList.get(0).column;
        final String oldValue = getEntry().getAsString(column);
        final DataKind kind = getKind();

        final Calendar calendar = Calendar.getInstance(DateUtils.UTC_TIMEZONE, Locale.US);
        final int defaultYear = calendar.get(Calendar.YEAR);

        // Check whether the year is optional
        final boolean isYearOptional = getType().isYearOptional();

        final int oldYear, oldMonth, oldDay;
        if (TextUtils.isEmpty(oldValue)) {
            // Default to January first, 30 years ago
            oldYear = defaultYear;
            oldMonth = 0;
            oldDay = 1;
        } else {
            final ParsePosition position = new ParsePosition(0);
            // Try parsing with year
            Date date1 = kind.dateFormatWithYear.parse(oldValue, position);
            if (date1 == null) {
                // If that format does not fit, try guessing the right format
                date1 = DateUtils.parseDate(oldValue);
            }
            if (date1 != null) {
                calendar.setTime(date1);
                oldYear = calendar.get(Calendar.YEAR);
                oldMonth = calendar.get(Calendar.MONTH);
                oldDay = calendar.get(Calendar.DAY_OF_MONTH);
            } else {
                final Date date2 = kind.dateFormatWithoutYear.parse(oldValue, position);
                // Don't understand the date, lets not change it
                if (date2 == null) return null;
                calendar.setTime(date2);
                oldYear = isYearOptional ? 0 : defaultYear;
                oldMonth = calendar.get(Calendar.MONTH);
                oldDay = calendar.get(Calendar.DAY_OF_MONTH);
            }
        }
        final OnDateSetListener callBack = new OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                if (year == 0 && !isYearOptional) throw new IllegalStateException();
                final Calendar outCalendar =
                        Calendar.getInstance(DateUtils.UTC_TIMEZONE, Locale.US);

                // If no year specified, set it to 2000 (we could pick any leap year here).
                // The format string will ignore that year.
                // For formats other than Exchange, the time of the day is ignored
                outCalendar.clear();
                outCalendar.set(year == 0 ? 2000 : year, monthOfYear, dayOfMonth,
                        DEFAULT_HOUR, 0, 0);

                final String resultString;
                if (year == 0) {
                    resultString = kind.dateFormatWithoutYear.format(outCalendar.getTime());
                } else {
                    resultString = kind.dateFormatWithYear.format(outCalendar.getTime());
                }

                onFieldChanged(column, resultString);
                rebuildDateView();
            }
        };
        final DatePickerDialog resultDialog = new DatePickerDialog(getContext(), callBack,
                oldYear, oldMonth, oldDay, isYearOptional);
        return resultDialog;
    }

    /**
     * @return Default hour which should be used for birthday field.
     */
    public static int getDefaultHourForBirthday() {
        return DEFAULT_HOUR;
    }

    @Override
    public void clearAllFields() {
        // Update UI
        mDateView.setText(mNoDateString);
        mDateView.setTextColor(mSecondaryTextColor);

        // Update state
        final String column = getKind().fieldList.get(0).column;
        onFieldChanged(column, "");
    }
}
