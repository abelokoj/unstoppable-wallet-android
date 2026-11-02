package io.horizontalsystems.walletkit.modules.approvals

enum class ApprovalScanAnimation {
    Together, Original, Scanner, Tom, Jerry;

    val next: ApprovalScanAnimation
        get() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromStoredIndex(index: Int): ApprovalScanAnimation = entries.getOrElse(index) { Together }
    }
}
