package com.sockc.billinsight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sockc.billinsight.ui.BillInsightApp
import com.sockc.billinsight.ui.BillInsightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BillInsightTheme {
                val vm: BillInsightViewModel = viewModel()
                BillInsightApp(vm)
            }
        }
    }
}
