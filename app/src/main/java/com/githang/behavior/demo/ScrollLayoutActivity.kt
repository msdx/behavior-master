package com.githang.behavior.demo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.ViewGroup
import com.githang.behavior.ScrollLayoutBehavior
import kotlinx.android.synthetic.main.activity_scroll_layout.*

/**
 * @author 黄浩杭 (msdx.android@qq.com)
 * @since 2018-11-27
 */
class ScrollLayoutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scroll_layout)
        val behavior = ScrollLayoutBehavior.from(frame_layout)
        behavior.setScrollLayoutCallback(object: ScrollLayoutBehavior.ScrollLayoutCallback() {
            override fun onStateChanged(viewGroup: ViewGroup, oldState: Int, newState: Int) {
            }

            override fun onSlide(viewGroup: ViewGroup, behavior: ScrollLayoutBehavior<*>?, position: Int) {
            }

        })
        frame_layout.post { behavior.showCollapsed() }
    }
}