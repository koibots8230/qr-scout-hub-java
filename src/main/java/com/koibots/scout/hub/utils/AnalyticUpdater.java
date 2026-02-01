package com.koibots.scout.hub.utils;

import java.io.IOException;

import com.koibots.scout.hub.Analytic;

public interface AnalyticUpdater {
    public void updateAnalytic(Analytic oldAnalytic, Analytic newAnalytic) throws IOException;
    public void deleteAnalytic(Analytic analytic) throws IOException;
}