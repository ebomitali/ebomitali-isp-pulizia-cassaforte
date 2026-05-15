trait ZosFileOps {
    abstract boolean exists(String path)
    abstract void    delete(String path)
    abstract void    copy(String src, String dst)
    abstract List<String> list(String container)
}
