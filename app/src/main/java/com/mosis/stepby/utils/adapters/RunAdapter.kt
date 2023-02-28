package com.mosis.stepby.utils.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.type.DateTime
import com.mosis.stepby.R
import com.mosis.stepby.utils.RunInfo
import com.mosis.stepby.utils.distanceToStringWithUnit
import com.mosis.stepby.utils.durationToString
import com.mosis.stepby.utils.timeStampToString
import java.time.format.DateTimeFormatter

class RunAdapter(var list: List<RunInfo>): RecyclerView.Adapter<RunAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return ViewHolder(layoutInflater.inflate(R.layout.one_run, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun setOnItemClickListener(clickListener: ClickListener) {
        mClickListener = clickListener
    }

    lateinit var mClickListener: ClickListener
    interface ClickListener { fun onClick(pos: Int, view: View)}

    inner class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val tvRunName = itemView.findViewById<TextView>(R.id.tvRunName)
        val tvTrackName = itemView.findViewById<TextView>(R.id.tvTrackName)
        val tvDateTime = itemView.findViewById<TextView>(R.id.tvDateTime)
        val tvDistanceValue = itemView.findViewById<TextView>(R.id.tvDistanceValue)
        val tvDistanceUnit = itemView.findViewById<TextView>(R.id.tvDistanceUnit)
        val tvDuration = itemView.findViewById<TextView>(R.id.tvDuration)

        init { itemView.setOnClickListener(this) }

        fun bind(model: RunInfo) {
            tvRunName.text = model.name
            tvDateTime.text = timeStampToString(model.finishedTS)
            val distancePair = distanceToStringWithUnit(model.distance)
            tvDistanceValue.text = distancePair.first
            tvDistanceUnit.text = distancePair.second
            tvDuration.text = durationToString(model.duration)

            if (model.trackID == null) tvTrackName.visibility = View.GONE
            else tvTrackName.text = "part of track"
        }

        override fun onClick(p0: View?) {
            mClickListener.onClick(adapterPosition, itemView)
        }

    }
}