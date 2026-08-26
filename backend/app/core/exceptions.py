class AnalysisException(Exception):
    def __init__(self, message: str, code: str = "analysis_failed"):
        self.message = message
        self.code = code
        super().__init__(message)

class ResourceNotFoundException(Exception):
    def __init__(self, message: str = "Resource not found", code: str = "not_found"):
        self.message = message
        self.code = code
        super().__init__(message)
