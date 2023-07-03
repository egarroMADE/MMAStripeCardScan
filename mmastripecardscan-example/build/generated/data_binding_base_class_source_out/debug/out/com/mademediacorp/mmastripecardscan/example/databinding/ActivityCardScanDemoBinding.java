// Generated by view binder compiler. Do not edit!
package com.mademediacorp.mmastripecardscan.example.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;
import com.mademediacorp.mmastripecardscan.example.R;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class ActivityCardScanDemoBinding implements ViewBinding {
  @NonNull
  private final CoordinatorLayout rootView;

  @NonNull
  public final CoordinatorLayout coordinator;

  @NonNull
  public final Button launchScanButton;

  @NonNull
  public final ScrollView scanResultScroll;

  @NonNull
  public final TextView scanResultText;

  private ActivityCardScanDemoBinding(@NonNull CoordinatorLayout rootView,
      @NonNull CoordinatorLayout coordinator, @NonNull Button launchScanButton,
      @NonNull ScrollView scanResultScroll, @NonNull TextView scanResultText) {
    this.rootView = rootView;
    this.coordinator = coordinator;
    this.launchScanButton = launchScanButton;
    this.scanResultScroll = scanResultScroll;
    this.scanResultText = scanResultText;
  }

  @Override
  @NonNull
  public CoordinatorLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static ActivityCardScanDemoBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static ActivityCardScanDemoBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.activity_card_scan_demo, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static ActivityCardScanDemoBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      CoordinatorLayout coordinator = (CoordinatorLayout) rootView;

      id = R.id.launchScanButton;
      Button launchScanButton = ViewBindings.findChildViewById(rootView, id);
      if (launchScanButton == null) {
        break missingId;
      }

      id = R.id.scanResultScroll;
      ScrollView scanResultScroll = ViewBindings.findChildViewById(rootView, id);
      if (scanResultScroll == null) {
        break missingId;
      }

      id = R.id.scanResultText;
      TextView scanResultText = ViewBindings.findChildViewById(rootView, id);
      if (scanResultText == null) {
        break missingId;
      }

      return new ActivityCardScanDemoBinding((CoordinatorLayout) rootView, coordinator,
          launchScanButton, scanResultScroll, scanResultText);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}