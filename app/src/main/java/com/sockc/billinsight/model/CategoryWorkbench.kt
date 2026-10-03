package com.sockc.billinsight.model

data class CategoryReviewItem(
    val transaction: Transaction,
    val proposedCategory: String?,
    val matchedTerm: String,
    val basis: String,
    val needsReview: Boolean,
    val manuallyEdited: Boolean,
    val purposeWarning:String? = null,
)

data class CategoryReviewGroup(
    val key: String,
    val label: String,
    val category: String?,
    val evidence: String,
    val items: List<CategoryReviewItem>,
    val batchEligible: Boolean,
) {
    val count: Int get() = items.size
    val changeableCount: Int get() = items.count {
        !it.manuallyEdited && it.proposedCategory != null &&
            it.proposedCategory != it.transaction.category
    }
}

data class AutoCategoryPreview(
    val scanned: Int = 0,
    val totalImported:Int = 0,
    val proposed: Int = 0,
    val unresolved: Int = 0,
    val protected: Int = 0,
    val groups: List<CategoryReviewGroup> = emptyList(),
)

data class CategoryChangeBatch(
    val id: Long,
    val changed: Int,
    val label: String,
)
