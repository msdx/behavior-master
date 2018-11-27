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
                // 在这里的根据所切换的状态，可以控制其他视图的显示隐藏
            }

            override fun onSlide(viewGroup: ViewGroup, behavior: ScrollLayoutBehavior<*>?, position: Int) {
                // 这里通过位置的回调，可以设置一些渐变的变化，比如设置状态栏或标题栏的显示或隐藏
            }

        })
        frame_layout.post { behavior.showCollapsed() }
    }
}