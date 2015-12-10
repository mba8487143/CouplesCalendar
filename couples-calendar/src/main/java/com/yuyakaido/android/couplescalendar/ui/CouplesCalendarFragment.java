package com.yuyakaido.android.couplescalendar.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.TextView;

import com.yuyakaido.android.couplescalendar.R;
import com.yuyakaido.android.couplescalendar.adapter.DateGridAdapter;
import com.yuyakaido.android.couplescalendar.model.CouplesCalendarEvent;
import com.yuyakaido.android.couplescalendar.model.Theme;
import com.yuyakaido.android.couplescalendar.util.CalendarUtils;

import com.yuyakaido.android.couplescalendar.util.EventCache;
import org.joda.time.DateTime;

import java.util.Date;
import java.util.List;

/**
 * Created by yuyakaido on 6/9/15.
 */
public class CouplesCalendarFragment extends Fragment implements
        AdapterView.OnItemClickListener,
        ViewPager.OnPageChangeListener {

    /**
     * 月が変わったことを通知するためのコールバックリスナー
     */
    public interface OnMonthChangeListener {
        public void onMonthChange(Date month);
    }

    /**
     * 日付が選択されたことを通知するためのコールバックリスナー
     */
    public interface OnDateClickListener {
        public void onDateClick(Date date);
    }

    private static final String ARGS_THEME = "ARGS_THEME";
    private static final int MONTH_VIEW_PAGER_MARGIN = 20;

    private OnDateClickListener mOnDateClickListener;
    private OnMonthChangeListener mOnMonthChangeListener;

    private Theme mTheme;
    private DateTime mToday;
    private GridView mDayOfWeekGridView;
    private ViewPager mMonthViewPager;
    private MonthViewPagerAdapter mMonthViewPagerAdapter;
    private View mPrevSelectedView;

    private EventCache mEventCache = new EventCache();

    public static CouplesCalendarFragment newInstance() {
        return CouplesCalendarFragment.newInstance(Theme.getDefaultTheme());
    }

    public static CouplesCalendarFragment newInstance(Theme theme) {
        if (theme == null) {
            theme = Theme.getDefaultTheme();
        }

        Bundle args = new Bundle();
        args.putSerializable(ARGS_THEME, theme);
        CouplesCalendarFragment fragment = new CouplesCalendarFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof OnDateClickListener) {
            mOnDateClickListener = (OnDateClickListener) activity;
        }
        if (activity instanceof OnMonthChangeListener) {
            mOnMonthChangeListener = (OnMonthChangeListener) activity;
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_couples_calendar, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mMonthViewPager = (ViewPager) view.findViewById(R.id.fragment_couples_calendar_view_pager);
        mDayOfWeekGridView = (GridView) view.findViewById(R.id.fragment_couples_calendar_day_of_week_grid_view);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Bundle args = getArguments();
        mTheme = (Theme) args.getSerializable(ARGS_THEME);

        mToday = CalendarUtils.getZonedMidnight();

        // 月表示
        mMonthViewPagerAdapter = new MonthViewPagerAdapter();
        mMonthViewPager.setAdapter(mMonthViewPagerAdapter);
        mMonthViewPager.setPageMargin(CalendarUtils.dp2px(getActivity(), MONTH_VIEW_PAGER_MARGIN));
        mMonthViewPager.setOnPageChangeListener(this);
        mMonthViewPager.setCurrentItem(MonthViewPagerAdapter.PAGER_CENTER_COUNT);

        // 曜日表示
        mDayOfWeekGridView.setAdapter(new DayOfWeekGridAdapter());
    }

    /**
     * 日付を選択した際に呼び出される
     * @param adapterView
     * @param view
     * @param position
     * @param id
     */
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        // Tagから選択された日付とViewHolderを取り出す
        DateTime selectedDateTime = (DateTime) view.getTag(R.id.date_grid_view_tag_key_date_time);
        DateGridAdapter.ViewHolder holder = (DateGridAdapter.ViewHolder) view.getTag(R.id.date_grid_view_tag_key_view_holder);

        // 曜日の関係で空白となっている日付を選択した場合は何もしない
        if (selectedDateTime == null) {
            return;
        }

        activateSelectedDate(holder);

        // 前回選択していた日付のハイライトをリセットする
        if (mPrevSelectedView != null && !mPrevSelectedView.equals(view)) {
            deactivatePrevSelectedView();
        }

        // 日付が選択されたことをリスナーに通知する
        notifyDateClick(selectedDateTime);

        // 他の日付が選択された時に状態をリセットするために今回選択された日付セルを保存しておく
        mPrevSelectedView = view;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

    @Override
    public void onPageScrollStateChanged(int state) {}

    @Override
    public void onPageSelected(int position) {
        final DateTime month = mMonthViewPagerAdapter.getDateTime(position);
        if (mEventCache.isInitialized()) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    // イベントのキャッシュを作る
                    if (month.isAfter(mToday)) {
                        mEventCache.createCacheIn(month.plusMonths(1));
                    } else {
                        mEventCache.createCacheIn(month.minusMonths(1));
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    super.onPostExecute(result);
                    if (isDetached()) {
                        return;
                    }

                    // カレンダーを更新する
                    update();
                }
            }.execute();

        }

        // 月が変わったことをリスナーに通知する
        notifyMonthChange(month);
    }

    /**
     * イベントを設定する
     * @param events
     */
    public void setEvents(final List<CouplesCalendarEvent> events) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                mEventCache.init(events, mMonthViewPagerAdapter.getDateTime(mMonthViewPager.getCurrentItem()));
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                super.onPostExecute(result);
                if (isDetached()) {
                    return;
                }

                update();
            }
        }.execute();
    }

    /**
     * カレンダーを再描画する
     */
    private void update() {
        mMonthViewPagerAdapter.update(mMonthViewPager.getCurrentItem());
    }

    /**
     * 以前に選択されていた日付セルを非選択状態にする
     */
    private void deactivatePrevSelectedView() {
        DateGridAdapter.ViewHolder holder = (DateGridAdapter.ViewHolder) mPrevSelectedView
                .getTag(R.id.date_grid_view_tag_key_view_holder);
        holder.date.setChecked(false);
        holder.firstEvent.setChecked(false);
        holder.secondEvent.setChecked(false);
        holder.thirdEventBase.setColorFilter(getResources()
                .getColor(mTheme.getSelectedCellColorId()), PorterDuff.Mode.SRC_IN);
        holder.thirdEventPlus.setColorFilter(getResources()
                .getColor(R.color.cc_while), PorterDuff.Mode.SRC_IN);
        holder.background.setChecked(false);
    }

    /**
     * 日付セルを選択状態にする
     * @param holder
     */
    private void activateSelectedDate(DateGridAdapter.ViewHolder holder) {
        holder.date.setChecked(true);
        holder.firstEvent.setChecked(true);
        holder.secondEvent.setChecked(true);
        holder.thirdEventBase.setColorFilter(getResources()
                .getColor(R.color.cc_while), PorterDuff.Mode.SRC_IN);
        holder.thirdEventPlus.setColorFilter(getResources()
                .getColor(mTheme.getSelectedCellColorId()), PorterDuff.Mode.SRC_IN);
        holder.background.setChecked(true);
    }

    /**
     * 表示されている月をリスナーに通知する
     * @param month
     */
    private void notifyMonthChange(final DateTime month) {
        if (mOnMonthChangeListener != null) {
            mOnMonthChangeListener.onMonthChange(month.toDate());
        }
    }

    /**
     * 日付セルが選択されたことをリスナーに通知する
     * @param selectedDateTime
     */
    private void notifyDateClick(DateTime selectedDateTime) {
        if (mOnDateClickListener != null) {
            mOnDateClickListener.onDateClick(selectedDateTime.toDate());
        }
    }

    /**
     * 月の日付グリッドを表示するためのアダプタ
     */
    private class MonthViewPagerAdapter extends PagerAdapter {

        private static final int OFFSET_LIMIT = 3;
        private static final int MONTH_COUNT = 12;

        public static final int MAX_PAGE_COUNT = MONTH_COUNT * 10000;
        public static final int PAGER_CENTER_COUNT = MAX_PAGE_COUNT / 2;

        private LayoutInflater mLayoutInflater;
        private DateGridAdapter[] mDateGridAdapters = new DateGridAdapter[OFFSET_LIMIT];

        public MonthViewPagerAdapter() {
            mLayoutInflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View instantiateItem(ViewGroup container, int position) {
            DateGridAdapter dateGridAdapter = new DateGridAdapter(
                    getActivity(),
                    mEventCache,
                    mTheme,
                    getDateTime(position),
                    mToday);
            mDateGridAdapters[position % OFFSET_LIMIT] = dateGridAdapter;
            GridView gridView = (GridView) mLayoutInflater.inflate(
                    R.layout.item_month_view_pager, container, false);
            gridView.setAdapter(dateGridAdapter);
            gridView.setOnItemClickListener(CouplesCalendarFragment.this);
            container.addView(gridView);
            return gridView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            GridView gridView = (GridView) object;
            gridView.setAdapter(null);
            gridView.setOnItemClickListener(null);
            container.removeView(gridView);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view.equals(object);
        }

        @Override
        public int getCount() {
            return MAX_PAGE_COUNT;
        }

        /**
         * 引数で渡したpositionに対応する月を更新する
         * @param position
         */
        public void update(int position) {
            mDateGridAdapters[position % OFFSET_LIMIT].notifyDataSetChanged();
        }

        /**
         * positionに対応する月を計算する
         * @param position
         * @return
         */
        public DateTime getDateTime(int position) {
            int difference = position - PAGER_CENTER_COUNT;
            return mToday.plusMonths(difference);
        }

    }

    /**
     * 曜日を表示するためのアダプタ
     */
    private class DayOfWeekGridAdapter extends ArrayAdapter<String> {

        private LayoutInflater mLayoutInflater;

        public DayOfWeekGridAdapter() {
            super(getActivity(), 0, getActivity().getResources().getStringArray(R.array.day_of_weeks));
            mLayoutInflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;

            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.item_day_of_week_grid, parent, false);

                holder = new ViewHolder();
                holder.textView = (TextView) convertView.findViewById(R.id.item_day_of_week_grid_text_view);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.textView.setText(getItem(position));

            return convertView;
        }

        private class ViewHolder {
            public TextView textView;
        }

    }

}
