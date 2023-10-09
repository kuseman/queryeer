-- Script used to fetch meta data from SQL Server for code complete / tooltips etc.

-- Tables/views/synonyms/TVF + columns
SELECT s.name         objectSchema
,      DB_NAME()      objectCatalog
,      ot.name        objectName
,      LTRIM(RTRIM(ot.type))
                      objectType
,      oc.name        columnName
,      t.name         columnType
,      oc.max_length  columnMaxLength
,      oc.precision   columnPrecision
,      oc.scale       columnScale
,      oc.is_nullable columnNullable

,      i.name         primaryKeyName

FROM sys.all_objects ot
INNER JOIN sys.all_columns oc
  ON oc.object_id = ot.object_id
INNER JOIN sys.[schemas] s
  ON s.schema_id = ot.schema_id
INNER JOIN sys.types t
  ON t.user_type_id = oc.user_type_id

LEFT JOIN sys.index_columns ic 
  ON  ic.object_id = oc.object_id
  AND ic.column_id = oc.column_id

 LEFT JOIN sys.indexes i
  ON i.object_id = ic.object_id
  AND i.index_id = ic.index_id
  AND i.is_primary_key = 1
  
WHERE ot.type IN ('V', 'U', 'SN', 'TF', 'IF')
ORDER BY s.name, ot.name, oc.column_id

-- Procedures/functions + parameters
SELECT s.name         objectSchema
,      DB_NAME()      objectCatalog
,      ot.name        objectName
,      LTRIM(RTRIM(ot.type))
                      objectType
,      p.name         parameterName
,      t.name         parameterType
,      p.max_length   parameterMaxLength
,      p.precision    parameterPrecision
,      p.scale        parameterScale
,      p.is_nullable  parameterNullable
,      p.is_output    parameterOutput
FROM sys.all_objects ot
INNER JOIN sys.[schemas] s
  ON s.schema_id = ot.schema_id
INNER JOIN sys.[parameters] p
  ON p.object_id = ot.object_id
INNER JOIN sys.types t
  ON t.user_type_id = p.user_type_id
WHERE ot.type IN ('P', 'FN')
ORDER BY s.name, ot.name, p.parameter_id

-- Indices
SELECT SCHEMA_NAME(oc.schema_id) objectSchema
,      DB_NAME()                 objectCatalog
,      oc.name                   objectName
,      ind.name                  indexName
,      ind.is_unique             indexIsUnique
,      ic.is_descending_key      columnDescending
,      col.name                  columnName
FROM sys.indexes ind
INNER JOIN sys.all_objects oc
  ON oc.object_id = ind.object_id
INNER JOIN sys.index_columns ic 
  ON ic.object_id = ind.object_id
  AND ind.index_id = ic.index_id
INNER JOIN sys.columns col 
  ON ic.object_id = col.object_id
  AND ic.column_id = col.column_id 
WHERE ind.is_primary_key = 0
AND ind.index_id > 0 -- Skip HEAP indices
ORDER BY oc.schema_id, ind.name, ic.key_ordinal

-- Foreign keys
SELECT SCHEMA_NAME(obj.schema_id)  objectSchema
,    obj.name                      objectName
,    DB_NAME()                     objectCatalog

,    col1.name                     constrainedColumn
,    SCHEMA_NAME(tab1.schema_id)   constrainedObjectSchema 
,    DB_NAME()                     constrainedObjectCatalog
,    tab1.name                     constrainedObjectName

,    col2.name                     referencedColumn
,    SCHEMA_NAME(tab2.schema_id)   referencedObjectSchema
,    DB_NAME()                     referencedObjectCatalog
,    tab2.name                     referencedObjectName

FROM sys.foreign_key_columns fkc
INNER JOIN sys.objects obj
  ON obj.object_id = fkc.constraint_object_id
INNER JOIN sys.tables tab1
  ON tab1.object_id = fkc.parent_object_id
INNER JOIN sys.columns col1
  ON col1.column_id = parent_column_id
  AND col1.object_id = tab1.object_id
INNER JOIN sys.tables tab2
  ON tab2.object_id = fkc.referenced_object_id
INNER JOIN sys.columns col2
  ON col2.column_id = referenced_column_id
  AND col2.object_id = tab2.object_id
ORDER BY obj.schema_id, obj.name

-- Constraints
SELECT SCHEMA_NAME(t.schema_id) objectSchema
,    DB_NAME()                  objectCatalog
,    t.name                     objectName
,    con.name                   name
,    CASE WHEN con.type = 'D' THEN 'DEFAULT'
          WHEN con.type = 'C' THEN 'CHECK'
          END                   type
,    c.name                     columnName
,    con.definition             definition
FROM sys.tables t
INNER JOIN 
(
  SELECT cc.name, cc.object_id, cc.parent_object_id, cc.definition, cc.parent_column_id, cc.type
  FROM sys.check_constraints cc

  UNION

  SELECT  dc.name, dc.object_id, dc.parent_object_id, dc.definition, dc.parent_column_id, dc.type
  from sys.default_constraints dc
) con
  ON con.parent_object_id = t.object_id
INNER JOIN sys.columns c
  ON  c.object_id = con.parent_object_id
  AND c.column_id = con.parent_column_id
ORDER BY t.name