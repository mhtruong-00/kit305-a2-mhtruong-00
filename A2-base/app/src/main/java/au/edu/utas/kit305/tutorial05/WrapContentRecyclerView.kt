package au.edu.utas.kit305.tutorial05

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

class WrapContentRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var dataObserver: AdapterDataObserver? = null

    override fun setAdapter(adapter: Adapter<*>?) {
        dataObserver?.let { old -> this.adapter?.unregisterAdapterDataObserver(old) }

        super.setAdapter(adapter)

        if (adapter == null) {
            dataObserver = null
            return
        }

        val observer = object : AdapterDataObserver() {
            override fun onChanged() = requestLayout()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = requestLayout()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = requestLayout()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = requestLayout()
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = requestLayout()
        }
        adapter.registerAdapterDataObserver(observer)
        dataObserver = observer
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        // Allow RecyclerView inside ScrollView to measure all children.
        val expandedHeightSpec = MeasureSpec.makeMeasureSpec(Int.MAX_VALUE shr 2, MeasureSpec.AT_MOST)
        super.onMeasure(widthSpec, expandedHeightSpec)
    }
}

