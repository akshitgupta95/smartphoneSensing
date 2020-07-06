/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package com.tudelft.smartphonesensing.util;

import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

//import org.dpppt.android.calibration.R;

public class DialogUtil {

	public static void showConfirmDialog(Context context, @StringRes int title,
			DialogInterface.OnClickListener positiveClickListener) {
		new AlertDialog.Builder(context)
				.setTitle(title)
				.setMessage("Do you really want to continue?")
				.setPositiveButton("Continue", (dialog, which) -> {
					dialog.dismiss();
					positiveClickListener.onClick(dialog, which);
				})
				.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
				.show();
	}

	public static void showMessageDialog(Context context, String title, String msg) {
		new AlertDialog.Builder(context)
				.setTitle(title)
				.setMessage(msg)
				.setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
				.show();
	}

}
