package au.edu.utas.kit305.tutorial05

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class QuoteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quote)

        val lblQuoteTitle: TextView = findViewById(R.id.lblQuoteTitle)
        val lblQuoteStatus: TextView = findViewById(R.id.lblQuoteStatus)

        val houseName = intent.getStringExtra(HOUSE_NAME_EXTRA)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.quote_title_default)

        title = getString(R.string.quote_title_default)
        lblQuoteTitle.text = houseName
        lblQuoteStatus.text = getString(R.string.quote_loading)
    }
}

