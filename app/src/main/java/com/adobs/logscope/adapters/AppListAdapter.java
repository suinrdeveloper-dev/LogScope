package com.adobs.logscope.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.adobs.logscope.R;
import com.adobs.logscope.models.AppInfo;

import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    // SAFETY 1: Immutable References
    private final List<AppInfo> appList;
    private final OnAppClickListener listener;

    public interface OnAppClickListener {
        void onAppClick(AppInfo app);
    }

    public AppListAdapter(@NonNull List<AppInfo> appList, @NonNull OnAppClickListener listener) {
        this.appList = appList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        // Listener और List का रेफरेन्स ViewHolder को पास करना
        return new ViewHolder(view, listener, appList);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = appList.get(position);
        
        // SAFETY 2: Using Encapsulated Getters (चूंकि Model अब सुरक्षित है)
        holder.tvAppName.setText(app.getAppName());
        holder.tvPackageName.setText(app.getPackageName());
        holder.imgIcon.setImageDrawable(app.getIcon());
    }

    @Override
    public int getItemCount() {
        return appList == null ? 0 : appList.size();
    }

    /**
     * ViewHolder Class
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAppName;
        final TextView tvPackageName;
        final ImageView imgIcon;

        public ViewHolder(@NonNull View itemView, OnAppClickListener listener, List<AppInfo> appList) {
            super(itemView);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvPackageName = itemView.findViewById(R.id.tvPackageName);
            imgIcon = itemView.findViewById(R.id.imgIcon);

            // SAFETY 3: Memory Optimization
            // Click Listener सिर्फ एक बार (Creation के समय) अटैच होता है।
            itemView.setOnClickListener(v -> {
                // getBindingAdapterPosition() लेटेस्ट Android standard है (getAdapterPosition deprecated है)
                int position = getBindingAdapterPosition();
                
                // यह चेक करना बहुत जरूरी है कि आइटम डिलीट या स्वाइप तो नहीं हो गया
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onAppClick(appList.get(position));
                }
            });
        }
    }
}
