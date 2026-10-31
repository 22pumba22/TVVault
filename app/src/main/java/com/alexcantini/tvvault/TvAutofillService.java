// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.app.PendingIntent;
import android.app.assist.AssistStructure;
import android.content.Intent;
import android.os.CancellationSignal;
import android.os.Build;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillContext;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

public class TvAutofillService extends AutofillService {

    private static final String PLACEHOLDER = "PLACEHOLDER";

    private static class ParseResult {
        String packageName = "";
        final BwFieldCollection collection = new BwFieldCollection();
    }

    @Override
    public void onFillRequest(
            FillRequest request,
            CancellationSignal cancellationSignal,
            FillCallback callback
    ) {
        try {

            List<FillContext> contexts = request.getFillContexts();
            if (contexts == null || contexts.isEmpty()) {
                callback.onSuccess(null);
                return;
            }

            AssistStructure structure = contexts.get(contexts.size() - 1).getStructure();
            if (structure == null) {
                callback.onSuccess(null);
                return;
            }

            ParseResult parsed = new ParseResult();
            if (structure.getActivityComponent() != null) {
                parsed.packageName = structure.getActivityComponent().getPackageName();
            }

            for (int i = 0; i < structure.getWindowNodeCount(); i++) {
                parseNode(structure.getWindowNodeAt(i).getRootViewNode(), parsed);
            }

            // Never offer Autofill inside TV Vault itself.
            if (getPackageName().equals(parsed.packageName)) {
                callback.onSuccess(null);
                return;
            }

            // User-managed app exclusion list.
            if (new ExclusionStore(this).isExcluded(parsed.packageName)) {
                callback.onSuccess(null);
                return;
            }


            if (parsed.collection.autofillIds.isEmpty()) {
                callback.onSuccess(null);
                return;
            }

            if (!parsed.collection.fillableForLogin()) {
                callback.onSuccess(null);
                return;
            }

            /*
             * Faithful Bitwarden-style vault dataset:
             * show the vault independently of whether a local item already
             * matches the package. Credential filtering happens after selection.
             */
            /*
             * IMPORTANT: RemoteViews must reference a layout that belongs to
             * this package. Previous builds incorrectly paired our package name
             * with android.R.layout.simple_list_item_2. Android accepted the
             * Dataset but could not materialize a visible presentation.
             *
             * Bitwarden legacy uses exactly this pattern: its own package +
             * its own autofill_listitem layout.
             */
            RemoteViews presentation = new RemoteViews(
                    getPackageName(),
                    R.layout.autofill_listitem
            );
            presentation.setTextViewText(
                    R.id.autofill_title,
                    getString(R.string.fill_with_vault)
            );
            presentation.setTextViewText(
                    R.id.autofill_subtitle,
                    getString(R.string.open_local_vault)
            );

            Intent intent = new Intent(this, AutofillAuthActivity.class);
            intent.putExtra(AutofillAuthActivity.EXTRA_PACKAGE, parsed.packageName);

            ArrayList<AutofillId> ids = new ArrayList<>();
            ArrayList<String> kinds = new ArrayList<>();

            List<BwField> usernames = parsed.collection.usernameFields();
            List<BwField> passwords = parsed.collection.passwordFields();

            for (BwField f : parsed.collection.fields) {
                String kind = passwords.contains(f) ? "password" : usernames.contains(f) ? "username" : null;
                if (kind == null) continue;
                ids.add(f.autofillId);
                kinds.add(kind);
            }
            if (ids.isEmpty()) { callback.onSuccess(null); return; }

            intent.putParcelableArrayListExtra(AutofillAuthActivity.EXTRA_IDS, ids);
            intent.putStringArrayListExtra(AutofillAuthActivity.EXTRA_KINDS, kinds);

            /*
             * Bitwarden legacy requests a mutable authentication PendingIntent.
             * On Android 9 the mutability flag is not needed, so use the classic
             * CancelCurrent behavior that is compatible with API 28.
             */
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    9009,
                    intent,
                    PendingIntent.FLAG_CANCEL_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0)
            );

            Dataset.Builder dataset = new Dataset.Builder(presentation);
            dataset.setAuthentication(pendingIntent.getIntentSender());

            for (AutofillId id : ids) {
                dataset.setValue(id, AutofillValue.forText(PLACEHOLDER), presentation);
            }

            FillResponse.Builder response = new FillResponse.Builder();
            response.addDataset(dataset.build());

            if (!parsed.collection.ignoredIds.isEmpty()) {
                AutofillId[] ignored = parsed.collection.ignoredIds.toArray(new AutofillId[0]);
                response.setIgnoredIds(ignored);
            }

            callback.onSuccess(response.build());

        } catch (Throwable t) {
            callback.onFailure(getString(R.string.autofill_error));
        }
    }

    private void parseNode(AssistStructure.ViewNode node, ParseResult parsed) {
        if (node == null) return;

        if ((parsed.packageName == null || parsed.packageName.isEmpty())
                && node.getIdPackage() != null
                && !"android".equals(node.getIdPackage())) {
            parsed.packageName = node.getIdPackage();
        }

        AutofillId id = node.getAutofillId();
        String className = node.getClassName() == null ? "" : node.getClassName().toString();
        String[] hints = node.getAutofillHints();
        boolean hasHints = hints != null && hints.length > 0;

        boolean htmlInput = node.getHtmlInfo() != null
                && "input".equalsIgnoreCase(node.getHtmlInfo().getTag());

        boolean exactBitwardenEditText = "android.widget.EditText".equals(className);

        /*
         * Bitwarden legacy admission rules, plus one Android-TV-specific rule:
         * if the Android Autofill framework marks a custom View as TEXT,
         * treat it as a field even when its obfuscated class is not EditText.
         */
        boolean frameworkTextField = node.getAutofillType() == View.AUTOFILL_TYPE_TEXT;

        boolean add = exactBitwardenEditText || htmlInput || hasHints || frameworkTextField;

        if (id != null && add) {
            parsed.collection.add(new BwField(node));
        } else if (id != null) {
            parsed.collection.ignoredIds.add(id);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            parseNode(node.getChildAt(i), parsed);
        }
    }

    @Override
    public void onSaveRequest(SaveRequest request, SaveCallback callback) {
        callback.onSuccess();
    }
}
