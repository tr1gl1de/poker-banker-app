package app.feltbook.feature.handover

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.feltbook.core.domain.engine.HandoverAct
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.transfer.Endpoint
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.handover.receive.AcceptError
import app.feltbook.feature.handover.receive.IncomingSummary
import app.feltbook.feature.handover.receive.PlayerOption
import app.feltbook.feature.handover.receive.ReceiveFailure
import app.feltbook.feature.handover.receive.ReceiveHandoverScreen
import app.feltbook.feature.handover.receive.ReceivePhase
import app.feltbook.feature.handover.receive.ReceiveUiState
import app.feltbook.feature.handover.send.SendDialog
import app.feltbook.feature.handover.send.SendFailure
import app.feltbook.feature.handover.send.SendHandoverScreen
import app.feltbook.feature.handover.send.SendKind
import app.feltbook.feature.handover.send.SendPhase
import app.feltbook.feature.handover.send.SendUiState
import app.feltbook.feature.handover.send.SentHandover

/** Данные превью и скриншот-тестов — цифры макета Handover (не показываются в приложении). */
object PreviewHandoverData {
    /** Макет: касса 950, рейк 1 250, долги 9 200, доход 600 (забрать нельзя — «после расчёта долгов»). */
    val act = HandoverAct(
        fromBanker = "Андрей",
        cashOnHand = 950,
        rakeDue = 1_250,
        playersOwe = 9_200,
        bankerOwes = 0,
        fromBankerIncome = 600,
        incomeTakeable = 0,
        playersAtTable = 6,
        chipsInPlay = 8_300,
        entryCount = 23,
    )

    /** Доход можно оставить себе (касса покрывает рейк). */
    val actWithIncome = act.copy(cashOnHand = 4_300, rakeDue = 600, bankerOwes = 300, incomeTakeable = 600)

    val code = SendUiState(
        phase = SendPhase.Code(peerName = "Влад", digits = "4719", waitingPeer = false),
        gameTitle = "Пятничный стол",
        act = act,
        rules = GameRules.DEFAULT,
        players = 6,
    )

    val codeKeepIncome = code.copy(act = actWithIncome, keepIncome = true)

    val searching = code.copy(phase = SendPhase.Searching)

    val choose = code.copy(phase = SendPhase.Choose(listOf(Endpoint("1", "Влад"), Endpoint("2", "Егор"))))

    val sending = code.copy(phase = SendPhase.Sending(peerName = "Влад", fraction = 0.38f))

    val awaiting = code.copy(phase = SendPhase.AwaitingAccept(peerName = "Влад"))

    val permission = code.copy(phase = SendPhase.NeedsPermission)

    val failed = code.copy(
        phase = SendPhase.Failed(SendFailure.CONNECTION, ackLost = true),
        sent = SentHandover(toBanker = "Влад", cashHandedOver = 950, incomeTaken = 0, entries = 23),
    )

    val blocked = code.copy(phase = SendPhase.Failed(SendFailure.PERMISSION_BLOCKED))

    val noGms = code.copy(phase = SendPhase.Failed(SendFailure.NO_GMS))

    val fileSent = failed.copy(dialog = SendDialog.FileSent)

    val done = code.copy(phase = SendPhase.Done(peerName = "Влад", cash = 950))

    val template = SendUiState(
        phase = SendPhase.Code(peerName = "Влад", digits = "0583", waitingPeer = false),
        kind = SendKind.TEMPLATE,
        gameTitle = "Пятничный стол",
        rules = GameRules.DEFAULT,
        templateNames = 6,
    )

    private val players = listOf("Андрей", "Борис", "Влад", "Дима", "Егор", "Саша")
        .mapIndexed { index, name -> PlayerOption(id = index.toString(), name = name) }

    val incoming = IncomingSummary(
        isTemplate = false,
        title = "Пятничный стол",
        fromBanker = "Андрей",
        playersAtTable = 6,
        chipsInPlay = 12_000,
        cash = 4_300,
        players = players,
        rules = GameRules.DEFAULT,
    )

    val review = ReceiveUiState(phase = ReceivePhase.Review, summary = incoming, playAs = "2")

    val reviewError = review.copy(playAs = null, error = AcceptError.ANOTHER_GAME_ACTIVE)

    val reviewSenderLost = review.copy(senderDisconnected = true)

    val reviewFile = ReceiveUiState(
        phase = ReceivePhase.Review,
        fromFile = true,
        summary = incoming.copy(fileName = "Пятничный стол.feltbook"),
    )

    val reviewTemplate = ReceiveUiState(
        phase = ReceivePhase.Review,
        summary = IncomingSummary(
            isTemplate = true,
            title = "",
            fromBanker = "Андрей",
            rules = GameRules.DEFAULT,
            templateNames = 6,
        ),
    )

    val receiveCode =
        ReceiveUiState(phase = ReceivePhase.Code(peerName = "Андрей", digits = "4719", waitingPeer = false))

    val receiveWaiting = ReceiveUiState(phase = ReceivePhase.Waiting(localName = "Влад"))

    val receiveName = ReceiveUiState(phase = ReceivePhase.NeedsName(name = ""))

    val receiveCorrupted = ReceiveUiState(phase = ReceivePhase.Failed(ReceiveFailure.CORRUPTED), fromFile = true)
}

@Composable
fun SendPreview(state: SendUiState) {
    HandoverFrame(mode = HandoverMode.GIVE, onModeChange = {}, onBack = {}) {
        SendHandoverScreen(state = state, onAction = {})
    }
}

@Composable
fun ReceivePreview(state: ReceiveUiState) {
    HandoverFrame(mode = HandoverMode.RECEIVE, onModeChange = {}, onBack = {}) {
        ReceiveHandoverScreen(state = state, onAction = {})
    }
}

@Preview(widthDp = 390, heightDp = 844, locale = "ru")
@Composable
private fun SendCodePreview() {
    FeltbookTheme { SendPreview(PreviewHandoverData.code) }
}

@Preview(widthDp = 390, heightDp = 844, locale = "ru")
@Composable
private fun SendSearchingPreview() {
    FeltbookTheme { SendPreview(PreviewHandoverData.searching) }
}

@Preview(widthDp = 390, heightDp = 844, locale = "ru")
@Composable
private fun SendFailedPreview() {
    FeltbookTheme { SendPreview(PreviewHandoverData.failed) }
}

@Preview(widthDp = 390, heightDp = 844, locale = "ru")
@Composable
private fun SendTemplatePreview() {
    FeltbookTheme { SendPreview(PreviewHandoverData.template) }
}

@Preview(widthDp = 390, heightDp = 844, locale = "ru")
@Composable
private fun ReceiveReviewPreview() {
    FeltbookTheme { ReceivePreview(PreviewHandoverData.review) }
}

@Preview(widthDp = 390, heightDp = 844, locale = "ru")
@Composable
private fun ReceiveCodePreview() {
    FeltbookTheme { ReceivePreview(PreviewHandoverData.receiveCode) }
}

@Preview(widthDp = 390, heightDp = 844, locale = "ru", fontScale = 2f)
@Composable
private fun ReceiveReviewLargeFontPreview() {
    FeltbookTheme { ReceivePreview(PreviewHandoverData.review) }
}
