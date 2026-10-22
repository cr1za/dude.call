package com.cr1za.dudecall;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** No app window or overlay service: this is a real transparent Android home-screen widget. */
public final class DudeCallWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        super.onUpdate(context, manager, appWidgetIds);
        for (int appWidgetId : appWidgetIds) {
            Intent call = new Intent(context, WidgetCallActivity.class);
            call.setAction("com.cr1za.dudecall.WIDGET_CALL");
            PendingIntent tap = PendingIntent.getActivity(context, 0, call,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_button);
            views.setOnClickPendingIntent(R.id.widget_red_button, tap);
            views.setContentDescription(R.id.widget_red_button, "Call now");
            manager.updateAppWidget(appWidgetId, views);
        }
    }
}
