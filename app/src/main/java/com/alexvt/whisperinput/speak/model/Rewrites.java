package com.alexvt.whisperinput.speak.model;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.speech.RecognizerIntent;
import android.text.SpannableStringBuilder;
import android.util.Base64;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alexvt.whisperinput.R;
import com.alexvt.whisperinput.speak.activity.RewritesActivity;
import ee.ioc.phon.android.speechutils.Extras;
import ee.ioc.phon.android.speechutils.editor.Command;
import ee.ioc.phon.android.speechutils.editor.CommandMatcher;
import ee.ioc.phon.android.speechutils.editor.UtteranceRewriter;
import ee.ioc.phon.android.speechutils.utils.PreferenceUtils;

public class Rewrites {

    private static final Comparator SORT_BY_ID = new Rewrites.SortById();

    private final SharedPreferences mPrefs;
    private final Resources mRes;
    private final String mId;

    public Rewrites(SharedPreferences prefs, Resources res, String id) {
        mPrefs = prefs;
        mRes = res;
        mId = id;
    }

    public String getId() {
        return mId;
    }

    public String toString() {
        return mId;
    }

    public boolean isSelected() {
        return getDefaults().contains(mId);
    }

    public void setSelected(boolean b) {
        Set<String> set = new HashSet<>(getDefaults());
        if (set.contains(mId)) {
            if (!b) {
                set.remove(mId);
                putDefaults(set);
            }
        } else {
            if (b) {
                set.add(mId);
                putDefaults(set);
            }
        }
    }

    public Intent getK6neleIntent() {
        Intent intent = new Intent();
        intent.setClassName("com.alexvt.whisperinput", "com.alexvt.whisperinput.speak.activity.SpeechActionActivity");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, mId);
        intent.putExtra(Extras.EXTRA_AUTO_START, true);
        intent.putExtra(Extras.EXTRA_RESULT_REWRITES, new String[]{mId});
        return intent;
    }

    public Intent getShowIntent() {
        Intent intent = new Intent();
        intent.setClassName("com.alexvt.whisperinput", "com.alexvt.whisperinput.speak.activity.RewritesActivity");
        intent.putExtra(RewritesActivity.EXTRA_NAME, mId);
        return intent;
    }

    public Intent getSendIntent() {
        UtteranceRewriter ur = new UtteranceRewriter(getRewrites());
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_SUBJECT, mId);
        intent.putExtra(Intent.EXTRA_TEXT, ur.toTsv());
        // EXTRA_TITLE is shown in Android Q
        intent.putExtra(Intent.EXTRA_TITLE, mRes.getString(R.string.labelRewritesShare));
        intent.setType("text/tab-separated-values");
        return intent;
    }

    public Intent getIntentSendBase64() {
        UtteranceRewriter ur = new UtteranceRewriter(getRewrites());
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_SUBJECT, mId);
        intent.putExtra(Intent.EXTRA_TEXT, "k6://" + Base64.encodeToString(ur.toTsv().getBytes(), Base64.NO_WRAP | Base64.URL_SAFE));
        intent.setType("text/plain");
        return intent;
    }

    public SpannableStringBuilder[] getRules(CommandMatcher commandMatcher) {
        UtteranceRewriter.CommandHolder holder = getCommandHolder(commandMatcher);
        Collection<String> header = holder.getHeader().values();
        SpannableStringBuilder[] array = new SpannableStringBuilder[holder.size()];
        int i = 0;
        for (Command command : holder.getCommands()) {
            array[i++] = pp(command.toMap(header));
        }
        return array;
    }

    /**
     * Saves the rewrites under a new name. If the new name equals the old name, then nothing is done.
     * If the new name is null then the rewrites are deleted.
     *
     * @param newName New name of the rewrites or null if the rewrites should be deleted.
     */
    public void rename(String newName) {
        if (!mId.equals(newName)) {
            if (newName != null) {
                PreferenceUtils.putPrefMapEntry(mPrefs, mRes, R.string.keyRewritesMap, newName, getRewrites());
            }
            Set<String> deleteKeys = new HashSet<>();
            deleteKeys.add(mId);
            PreferenceUtils.clearPrefMap(mPrefs, mRes, R.string.keyRewritesMap, deleteKeys);
            // Replace the name in the defaults
            Set<String> defaults = new HashSet<>(getDefaults());
            if (defaults.remove(mId)) {
                if (newName != null) {
                    defaults.add(newName);
                }
                putDefaults(defaults);
            }
        }
    }

    public void delete() {
        rename(null);
    }

    public String getRewrites() {
        return PreferenceUtils.getPrefMapEntry(mPrefs, mRes, R.string.keyRewritesMap, mId);
    }

    private UtteranceRewriter.CommandHolder getCommandHolder(CommandMatcher matcher) {
        UtteranceRewriter ur = new UtteranceRewriter(getRewrites(), matcher);
        return ur.getCommandHolder();
    }

    private Set<String> getDefaults() {
        return PreferenceUtils.getPrefStringSet(mPrefs, mRes, R.string.defaultRewriteTables);
    }

    private void putDefaults(Set<String> set) {
        PreferenceUtils.putPrefStringSet(mPrefs, mRes, R.string.defaultRewriteTables, set);
    }

    public static String ppComboMatcher(String app, String locale, String service) {
        // The middot is between U+202F (NARROW NO-BREAK SPACE)
        return toPp(app) + " • " + toPp(locale) + " • " + toPp(service);
    }

    public static Set<String> getDefaults(SharedPreferences prefs, Resources res) {
        return PreferenceUtils.getPrefStringSet(prefs, res, R.string.defaultRewriteTables);
    }

    public static List<Rewrites> getTables(SharedPreferences prefs, Resources res) {
        List<String> rewritesIds = new ArrayList<>(PreferenceUtils.getPrefMapKeys(prefs, res, R.string.keyRewritesMap));
        List<Rewrites> rewritesTables = new ArrayList<>();
        for (String id : rewritesIds) {
            rewritesTables.add(new Rewrites(prefs, res, id));
        }
        Collections.sort(rewritesTables, SORT_BY_ID);
        return rewritesTables;
    }

    private static class SortById implements Comparator {

        public int compare(Object o1, Object o2) {
            Rewrites c1 = (Rewrites) o1;
            Rewrites c2 = (Rewrites) o2;
            return c1.getId().compareToIgnoreCase(c2.getId());
        }
    }

    /**
     * Pretty-print the rule, assuming that space-character sequences actually stand
     * for newline (single space) followed by tab (two spaces) sequences.
     * Sequence of 1 or 2 spaces is kept as it is.
     * <p>
     * TODO: map it to a layout file instead of using spans (?)
     *
     * @param map Mapping of rule component names to the corresponding components
     * @return pretty-printed rule
     */
    private static SpannableStringBuilder pp(Map<String, String> map) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        // Matcher with its 3 constraints
        ssb.append(ppMatcher(map));
        ssb.append('\n');
        // Utterance
        // int start = ssb.length();
        ssb.append(map.get(UtteranceRewriter.HEADER_UTTERANCE));
        // Use a layout file to make it easier to style the output
        // ssb.setSpan(new ForegroundColorSpan(0xffFAFAFA), start, ssb.length(), 0);
        ssb.append('\n');
        // Replacement
        ssb.append(toPp(map.get(UtteranceRewriter.HEADER_REPLACEMENT))
                .replace("         ", "\n\t\t\t\t")
                .replace("       ", "\n\t\t\t")
                .replace("     ", "\n\t\t")
                .replace("   ", "\n\t"));
        // Command with arguments
        ssb.append(ppCommand(map));
        // Label
        String label = map.get(UtteranceRewriter.HEADER_LABEL);
        if (label != null && !label.isEmpty()) {
            ssb.append("\n\n");
            ssb.append(label);
        }
        // Comment
        String comment = map.get(UtteranceRewriter.HEADER_COMMENT);
        if (comment != null && !comment.isEmpty()) {
            ssb.append("\n\n");
            // start = ssb.length();
            ssb.append(comment);
            // ssb.setSpan(new StyleSpan(Typeface.ITALIC), start, ssb.length(), 0);
        }
        return ssb;
    }

    private static String ppCommand(Map<String, String> map) {
        String id = map.get(UtteranceRewriter.HEADER_COMMAND);
        if (id == null || id.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        sb.append(id);
        String arg1 = toPp(map.get(UtteranceRewriter.HEADER_ARG1));
        if (!arg1.isEmpty()) {
            sb.append('\n');
            sb.append(' ');
            sb.append('(');
            sb.append(arg1);
            sb.append(')');
            String arg2 = toPp(map.get(UtteranceRewriter.HEADER_ARG2));
            if (!arg2.isEmpty()) {
                sb.append('\n');
                sb.append(' ');
                sb.append('(');
                sb.append(arg2);
                sb.append(')');
            }
        }
        return sb.toString();
    }

    private static String ppMatcher(Map<String, String> map) {
        return ppComboMatcher(map.get(UtteranceRewriter.HEADER_APP), map.get(UtteranceRewriter.HEADER_LOCALE), map.get(UtteranceRewriter.HEADER_SERVICE));
    }

    private static String toPp(String str) {
        return (str == null) ? "" : str;
    }
}