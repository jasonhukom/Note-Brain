package com.example.notebrain.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.notebrain.R
import com.example.notebrain.data.local.Note
import com.example.notebrain.utils.DateUtils

class NotesAdapter(
    private val onNoteClick: (Note) -> Unit
) : ListAdapter<Note, NotesAdapter.NoteViewHolder>(NoteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view, onNoteClick)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NoteViewHolder(
        itemView: View,
        private val onNoteClick: (Note) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)

        fun bind(note: Note) {
            if (note.title.isNotBlank()) {
                tvTitle.visibility = View.VISIBLE
                tvTitle.text = note.title
            } else {
                tvTitle.visibility = View.GONE
            }

            if (note.content.isNotBlank()) {
                tvContent.visibility = View.VISIBLE
                tvContent.text = note.content
            } else {
                tvContent.visibility = View.GONE
            }

            tvDate.text = DateUtils.formatDisplayDate(note.updatedAt)

            itemView.setOnClickListener {
                onNoteClick(note)
            }
        }
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem == newItem
        }
    }
}
