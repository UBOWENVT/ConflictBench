package com.dianping.cat.report.page.network;

import com.dianping.cat.report.ReportPage;
import org.unidal.web.mvc.view.BaseJspViewer;

public class JspViewer extends BaseJspViewer<ReportPage, Action, Context, Model> {
 @Override
 protected String getJspFilePath(Context ctx, Model model) {
		Action action = model.getAction();
 
		switch (action) {
		case NETWORK:
			return JspFile.NETWORK.getPath();
		case AGGREGATION:
<<<<<<< ours
			return JspFile.DASHBOARD.getPath();
		case NETTOPOLOGY:
			return JspFile.NETTOPOLOGY.getPath();
=======
			return JspFile.AGGREGATION.getPath();
>>>>>>> theirs
		}
 
		throw new RuntimeException("Unknown action: " + action);
	}
}