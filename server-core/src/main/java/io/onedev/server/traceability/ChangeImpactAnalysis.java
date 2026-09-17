package io.onedev.server.traceability;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Encapsula el resultado completo del análisis de impacto de un commit sobre los ConfigItems.
 */
public class ChangeImpactAnalysis implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String commitHash;
    private final String commitMessage;
    private final String authorName;
    private final ChangeTypeCategory category;
    private final String summary;
    private final List<ConfigItem> directlyChangedItems;
    private final List<ImpactedItem> impactedItems;
    private final boolean aiGenerated;

    public ChangeImpactAnalysis(String commitHash, String commitMessage, String authorName,
                                ChangeTypeCategory category, String summary,
                                List<ConfigItem> directlyChangedItems,
                                List<ImpactedItem> impactedItems,
                                boolean aiGenerated) {
        this.commitHash = Objects.requireNonNull(commitHash, "commitHash cannot be null");
        this.commitMessage = Objects.requireNonNull(commitMessage, "commitMessage cannot be null");
        this.authorName = Objects.requireNonNull(authorName, "authorName cannot be null");
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.summary = Objects.requireNonNull(summary, "summary cannot be null");
        this.directlyChangedItems = new ArrayList<ConfigItem>(directlyChangedItems);
        this.impactedItems = new ArrayList<ImpactedItem>(impactedItems);
        this.aiGenerated = aiGenerated;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public String getAuthorName() {
        return authorName;
    }

    public ChangeTypeCategory getCategory() {
        return category;
    }

    public String getSummary() {
        return summary;
    }

    public List<ConfigItem> getDirectlyChangedItems() {
        return Collections.unmodifiableList(directlyChangedItems);
    }

    public List<ImpactedItem> getImpactedItems() {
        return Collections.unmodifiableList(impactedItems);
    }

    public boolean isAiGenerated() {
        return aiGenerated;
    }

    /**
     * Determina si existe Drift (desfase): ocurre cuando se identificaron elementos
     * secundarios impactados que no vinieron modificados/sincronizados en el mismo commit.
     */
    public boolean hasDrift() {
        if (impactedItems.isEmpty()) {
            return false;
        }
        for (ImpactedItem impacted : impactedItems) {
            boolean alreadySynchronized = false;
            for (ConfigItem item : directlyChangedItems) {
                if (item.getType() == impacted.getTargetType()
                        && (item.getPath().equalsIgnoreCase(impacted.getSuggestedPath())
                            || impacted.getSuggestedPath().contains(item.getPath()))) {
                    alreadySynchronized = true;
                    break;
                }
            }
            if (!alreadySynchronized) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("ChangeImpactAnalysis[%s, %s, changed=%d, impacted=%d, drift=%s]", 
            commitHash, category, directlyChangedItems.size(), impactedItems.size(), hasDrift());
    }
}