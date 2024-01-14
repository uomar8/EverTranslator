package tw.firemaples.onscreenocr.data.usecase

import tw.firemaples.onscreenocr.data.repo.SettingRepository
import javax.inject.Inject

class GetHidingOCRAreaAfterTranslated @Inject constructor(
    private val settingRepository: SettingRepository,
) {
    operator fun invoke() = settingRepository.hideOCRAreaAfterTranslated()
}
