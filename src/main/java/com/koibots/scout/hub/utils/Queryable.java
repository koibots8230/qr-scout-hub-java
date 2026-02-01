package com.koibots.scout.hub.utils;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public interface Queryable {
    public List<Object[]> query(String query) throws IOException, SQLException;
}