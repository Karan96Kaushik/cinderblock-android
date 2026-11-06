package app.cinderblock

import android.app.Application
import app.cinderblock.data.Controller
import app.cinderblock.domain.CloudConfig

class CinderblockApp : Application() {
    lateinit var controller: Controller
        private set

    override fun onCreate() {
        super.onCreate()
        controller = Controller(this)
        controller.start(
            CloudConfig(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
                testFunctionUrl = BuildConfig.AMPLIFY_TEST_URL,
                aiChatUrl = BuildConfig.AI_CHAT_URL,
                aiExtractJsonUrl = BuildConfig.AI_EXTRACT_URL,
                aiScopeJudgeUrl = BuildConfig.AI_SCOPE_JUDGE_URL,
                reportIssueUrl = BuildConfig.REPORT_ISSUE_URL,
            ),
        )
    }
}
