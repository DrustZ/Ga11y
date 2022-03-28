package demo.AnnotationSystem.TalkbackUtilities;

import android.annotation.TargetApi;
import android.os.Build;
import android.text.Spannable;
import android.view.accessibility.AccessibilityNodeInfo;

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;

import demo.AnnotationSystem.R;

/**
 * Rule-based processor for {@link AccessibilityNodeInfoCompat}s.
 */
public class NodeSpeechRuleProcessor {
    /**
     * Returns the best description for the subtree rooted at
     * {@code announcedNode}.
     *
     * @param announcedNode The root node of the subtree to describe.
     * @param event The source event, may be {@code null} when called with
     *            non-source nodes.
     * @param source The event's source node.
     * @return The best description for a node.
     */
    public static CharSequence getDescriptionForTree(Context mContext, AccessibilityNodeInfoCompat announcedNode,
            AccessibilityEvent event, AccessibilityNodeInfoCompat source) {
        if (announcedNode == null) {
            return null;
        }

        final SpannableStringBuilder builder = new SpannableStringBuilder();

        Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
        appendDescriptionForTree(mContext, announcedNode, builder, event, source, visitedNodes);
        AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
        formatTextWithLabel(mContext, announcedNode, builder);
        appendRootMetadataToBuilder(mContext, announcedNode, builder);

        return builder;
    }

    private static void appendDescriptionForTree(Context mContext, AccessibilityNodeInfoCompat announcedNode,
            SpannableStringBuilder builder, AccessibilityEvent event,
            AccessibilityNodeInfoCompat source, Set<AccessibilityNodeInfoCompat> visitedNodes) {
        if (announcedNode == null) {
            return;
        }

        AccessibilityNodeInfoCompat visitedNode = AccessibilityNodeInfoCompat.obtain(announcedNode);
        if (!visitedNodes.add(visitedNode)) {
            visitedNode.recycle();
            return;
        }

        final AccessibilityEvent nodeEvent = (announcedNode.equals(source)) ? event : null;
        final CharSequence nodeDesc = AccessibilityNodeInfoUtils.getNodeText(announcedNode);
        final boolean blockChildDescription = hasOverridingContentDescription(announcedNode);

        SpannableStringBuilder childStringBuilder = new SpannableStringBuilder();

        if (!blockChildDescription) {
            // Recursively append descriptions for visible and non-focusable child nodes.
            ReorderedChildrenIterator iterator = ReorderedChildrenIterator
                    .createAscendingIterator(announcedNode);
            while (iterator.hasNext()) {
                AccessibilityNodeInfoCompat child = iterator.next();
                if (AccessibilityNodeInfoUtils.isVisible(child)
                        && !AccessibilityNodeInfoUtils.isAccessibilityFocusable(child)) {
                    appendDescriptionForTree(mContext, child, childStringBuilder, event, source,
                            visitedNodes);
                }
            }

            iterator.recycle();
        }

        // If any one of the following is satisfied:
        // 1. The root node has a description.
        // 2. The root has no override content description and the children have some description.
        // Then we should append the status information for this node.
        // This is used to avoid displaying checked/expanded status alone without node description.
        //
        if (!TextUtils.isEmpty(nodeDesc) || !TextUtils.isEmpty(childStringBuilder)) {
            appendExpandedOrCollapsedStatus(mContext, announcedNode, event, builder);
        }

        StringBuilderUtils.appendWithSeparator(builder, nodeDesc);
        StringBuilderUtils.appendWithSeparator(builder, childStringBuilder);
    }

    /**
     * Determines whether the node has a contentDescription that should cause its subtree's
     * description to be ignored.
     * The traditional behavior in TalkBack has been to return {@code true} for any node with a
     * non-empty contentDescription. In this function, we thus whitelist certain roles where it
     * doesn't make sense for the contentDescription to override the entire subtree.
     */
    private static boolean hasOverridingContentDescription(AccessibilityNodeInfoCompat node) {
        switch (Role.getRole(node)) {
            case Role.ROLE_PAGER:
            case Role.ROLE_GRID:
            case Role.ROLE_LIST:
                return false;
            default:
                return node != null && !TextUtils.isEmpty(node.getContentDescription());
        }
    }

    /**
     * If the supplied node has a label, replaces the builder text with a
     * version formatted with the label.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private static void formatTextWithLabel(Context mContext,
                                            AccessibilityNodeInfoCompat node, SpannableStringBuilder builder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return;

        // TODO: add getLabeledBy to support lib
        AccessibilityNodeInfo info = (AccessibilityNodeInfo) node.getInfo();
        if (info == null) return;
        AccessibilityNodeInfo labeledBy = info.getLabeledBy();
        if (labeledBy == null) return;
        AccessibilityNodeInfoCompat labelNode = new AccessibilityNodeInfoCompat(labeledBy);

        final SpannableStringBuilder labelDescription = new SpannableStringBuilder();
        Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
        appendDescriptionForTree(mContext, labelNode, labelDescription, null, null, visitedNodes);
        AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
        if (TextUtils.isEmpty(labelDescription)) {
            return;
        }

        final String labeled = mContext.getString(
                R.string.template_labeled_item, builder, labelDescription);
        Spannable spannableLabeledText = StringBuilderUtils.createSpannableFromTextWithTemplate(
                labeled, builder);

        // Replace the text of the builder.
        builder.clear();
        builder.append(spannableLabeledText);
    }

    /**
     * Appends meta-data about node's disabled state (if actionable).
     * <p>
     * This should only be applied to the root node of a tree.
     */
    private static void appendRootMetadataToBuilder(Context mContext,
                                                    AccessibilityNodeInfoCompat node, SpannableStringBuilder descriptionBuilder) {
        // Append state for actionable but disabled nodes.
        if (AccessibilityNodeInfoUtils.isActionableForAccessibility(node) && !node.isEnabled()) {
            StringBuilderUtils.appendWithSeparator(
                    descriptionBuilder, mContext.getString(R.string.value_disabled));
        }

        // Append the control's selected state.
        if (node.isSelected()) {
            StringBuilderUtils.appendWithSeparator(descriptionBuilder,
                    mContext.getString(R.string.value_selected));
        }
    }

    /**
     * Appends meta-data about the node's expandable/collapsible states.
     * <p>
     * This should be applied to all nodes in a tree, including the root.
     */
    private static void appendExpandedOrCollapsedStatus(Context mContext, AccessibilityNodeInfoCompat node,
                                                 AccessibilityEvent event,
                                                 SpannableStringBuilder descriptionBuilder) {
        // Append the control's expandable/collapsible state, if applicable.
        if (AccessibilityNodeInfoUtils.isExpandable(node)) {
            CharSequence collapsedString = mContext.getString(
                    R.string.value_collapsed);
            StringBuilderUtils.appendWithSeparator(descriptionBuilder, collapsedString);
        }
        if (AccessibilityNodeInfoUtils.isCollapsible(node)) {
            CharSequence expandedString = mContext.getString(
                    R.string.value_expanded);
            StringBuilderUtils.appendWithSeparator(descriptionBuilder, expandedString);
        }
    }
}
